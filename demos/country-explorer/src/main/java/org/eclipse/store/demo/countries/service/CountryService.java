package org.eclipse.store.demo.countries.service;

/*-
 * #%L
 * EclipseStore Demo Country Explorer
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.eclipse.store.demo.countries.index.LocationIndex;
import org.eclipse.store.demo.countries.index.CountryIndices;
import org.eclipse.store.demo.countries.model.Country;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.SpatialIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

/**
 * Core service that provides all country query operations for the Country Explorer demo.
 * <p>
 * On application startup this service loads country data from the classpath resource
 * {@code countries.json} into the {@link GigaMap} (unless the map already contains data).
 * It then exposes a rich set of query methods that leverage the {@code GigaMap}'s bitmap
 * and spatial indices for efficient lookups:
 * <ul>
 *   <li>Paginated listing of all countries</li>
 *   <li>Exact lookup by ISO&nbsp;3166-1 alpha-2 code</li>
 *   <li>Radius-based proximity search (by country or by coordinates)</li>
 *   <li>K-nearest-neighbour search</li>
 *   <li>Continent filtering</li>
 *   <li>Bounding-box search</li>
 *   <li>Hemisphere and tropical-zone queries</li>
 *   <li>Haversine distance calculation between two countries</li>
 * </ul>
 *
 * <p>All distance calculations use the
 * {@link SpatialIndexer#haversineDistance(double, double, double, double) haversine formula}
 * and are rounded to one decimal place (kilometres).
 *
 * @see org.eclipse.store.demo.countries.config.GigaMapConfig
 * @see CountryIndices
 */
@Service
public class CountryService
{
	private static final Logger LOG = LoggerFactory.getLogger(CountryService.class);

	private final GigaMap<Country> gigaMap;
	private final LocationIndex locationIndex;

	/**
	 * Constructs the service with its required dependencies.
	 *
	 * @param gigaMap       the {@link GigaMap} that stores and indexes country data
	 * @param locationIndex the spatial index used for geographic queries
	 */
	public CountryService(
		final GigaMap<Country> gigaMap,
		final LocationIndex locationIndex
	)
	{
		this.gigaMap       = gigaMap;
		this.locationIndex = locationIndex;
	}

	/**
	 * Initializes the {@link GigaMap} with country data from the classpath resource
	 * {@code countries.json}. If the map already contains data (e.g. from a previous
	 * run with persistent storage), this method is a no-op.
	 * <p>
	 * This method is invoked automatically by Spring after dependency injection via
	 * {@link PostConstruct}.
	 */
	@PostConstruct
	public void init()
	{
		if(!this.gigaMap.isEmpty())
		{
			LOG.info("GigaMap already contains {} countries, skipping initialization.", this.gigaMap.size());
			return;
		}

		final List<Country> countries = loadCountries();
		this.gigaMap.addAll(countries);
		LOG.info("Loaded {} countries into GigaMap.", this.gigaMap.size());
	}

	/**
	 * Returns a paginated list of all countries sorted alphabetically by name.
	 *
	 * @param page zero-based page index
	 * @param size number of countries per page
	 * @return a {@link PageResult} containing the requested slice and pagination metadata
	 */
	public PageResult list(final int page, final int size)
	{
		final long total = this.gigaMap.size();
		final List<Country> countries = this.gigaMap.query()
			.stream()
			.sorted(Comparator.comparing(Country::name))
			.skip((long) page * size)
			.limit(size)
			.toList();
		return new PageResult(countries, total, page, size);
	}

	/**
	 * Finds a single country by its ISO&nbsp;3166-1 alpha-2 code using the
	 * {@link CountryIndices#ALPHA2} bitmap index.
	 *
	 * @param alpha2 the two-letter country code (case-insensitive)
	 * @return the matching {@link Country}, or {@code null} if no match is found
	 */
	public Country findByAlpha2(final String alpha2)
	{
		return this.gigaMap.query(CountryIndices.ALPHA2.is(alpha2.toUpperCase()))
			.findFirst()
			.orElse(null);
	}

	/**
	 * Finds countries within the specified radius of the country identified by
	 * {@code alpha2}. The origin country itself is excluded from the result.
	 *
	 * @param alpha2   ISO&nbsp;3166-1 alpha-2 code of the origin country
	 * @param radiusKm search radius in kilometres
	 * @return a list of {@link NeighbourResult} entries sorted by ascending distance,
	 *         or an empty list if the origin country is not found
	 */
	public List<NeighbourResult> nearby(final String alpha2, final double radiusKm)
	{
		final Country origin = this.findByAlpha2(alpha2);
		if(origin == null)
		{
			return List.of();
		}
		return this.nearCoordinates(origin.latitude(), origin.longitude(), radiusKm)
			.stream()
			.filter(r -> !r.country().alpha2().equals(origin.alpha2()))
			.toList();
	}

	/**
	 * Finds countries within the specified radius of an arbitrary geographic point.
	 * <p>
	 * This method first uses the spatial index to obtain candidates, then applies a
	 * precise haversine-distance filter to remove false positives that fall outside
	 * the circular radius.
	 *
	 * @param lat      latitude of the search centre in decimal degrees
	 * @param lon      longitude of the search centre in decimal degrees
	 * @param radiusKm search radius in kilometres
	 * @return a list of {@link NeighbourResult} entries sorted by ascending distance
	 */
	public List<NeighbourResult> nearCoordinates(final double lat, final double lon, final double radiusKm)
	{
		return this.gigaMap.query(this.locationIndex.near(lat, lon, radiusKm))
			.stream()
			.filter(this.locationIndex.withinRadius(lat, lon, radiusKm))
			.map(c -> new NeighbourResult(
				c,
				Math.round(SpatialIndexer.haversineDistance(lat, lon, c.latitude(), c.longitude()) * 10.0) / 10.0
			))
			.sorted(Comparator.comparingDouble(NeighbourResult::distanceKm))
			.toList();
	}

	/**
	 * Returns the {@code k} nearest countries to the country identified by
	 * {@code alpha2}. Distance is computed with the haversine formula between
	 * the capital coordinates of the countries.
	 *
	 * @param alpha2 ISO&nbsp;3166-1 alpha-2 code of the origin country
	 * @param k      maximum number of nearest neighbours to return
	 * @return a list of up to {@code k} {@link NeighbourResult} entries sorted by
	 *         ascending distance, or an empty list if the origin is not found
	 */
	public List<NeighbourResult> kNearest(final String alpha2, final int k)
	{
		final Country origin = this.findByAlpha2(alpha2);
		if(origin == null)
		{
			return List.of();
		}
		final double lat = origin.latitude();
		final double lon = origin.longitude();
		return this.gigaMap.query()
			.stream()
			.filter(c -> !c.alpha2().equals(origin.alpha2()))
			.map(c -> new NeighbourResult(
				c,
				Math.round(SpatialIndexer.haversineDistance(lat, lon, c.latitude(), c.longitude()) * 10.0) / 10.0
			))
			.sorted(Comparator.comparingDouble(NeighbourResult::distanceKm))
			.limit(k)
			.toList();
	}

	/**
	 * Returns all countries belonging to the specified continent, using the
	 * {@link CountryIndices#CONTINENT} bitmap index.
	 *
	 * @param name continent name (e.g. {@code "Europe"}, {@code "Asia"})
	 * @return list of matching countries sorted alphabetically by name
	 */
	public List<Country> continent(final String name)
	{
		return this.gigaMap.query(CountryIndices.CONTINENT.is(name))
			.stream()
			.sorted(Comparator.comparing(Country::name))
			.toList();
	}

	/**
	 * Returns all countries whose capital coordinates fall inside the given
	 * latitude/longitude bounding box, using the spatial index.
	 *
	 * @param minLat southern boundary in decimal degrees
	 * @param maxLat northern boundary in decimal degrees
	 * @param minLon western boundary in decimal degrees
	 * @param maxLon eastern boundary in decimal degrees
	 * @return list of matching countries sorted alphabetically by name
	 */
	public List<Country> withinBox(
		final double minLat, final double maxLat,
		final double minLon, final double maxLon
	)
	{
		return this.gigaMap.query(this.locationIndex.withinBox(minLat, maxLat, minLon, maxLon))
			.stream()
			.sorted(Comparator.comparing(Country::name))
			.toList();
	}

	/**
	 * Returns all countries located in the specified hemisphere, sorted
	 * alphabetically by name.
	 * <p>
	 * Supported hemisphere values (case-insensitive):
	 * <ul>
	 *   <li>{@code "north"} &ndash; latitude &gt; 0</li>
	 *   <li>{@code "south"} &ndash; latitude &lt; 0</li>
	 *   <li>{@code "east"}  &ndash; longitude &gt; 0</li>
	 *   <li>{@code "west"}  &ndash; longitude &lt; 0</li>
	 * </ul>
	 *
	 * @param name hemisphere identifier
	 * @return list of matching countries, or an empty list for unrecognised values
	 */
	public List<Country> hemisphere(final String name)
	{
		return switch(name.toLowerCase())
		{
			case "north" -> this.gigaMap.query(this.locationIndex.latitudeAbove(0.0))
				.stream().sorted(Comparator.comparing(Country::name)).toList();
			case "south" -> this.gigaMap.query(this.locationIndex.latitudeBelow(0.0))
				.stream().sorted(Comparator.comparing(Country::name)).toList();
			case "east"  -> this.gigaMap.query(this.locationIndex.longitudeAbove(0.0))
				.stream().sorted(Comparator.comparing(Country::name)).toList();
			case "west"  -> this.gigaMap.query(this.locationIndex.longitudeBelow(0.0))
				.stream().sorted(Comparator.comparing(Country::name)).toList();
			default -> List.of();
		};
	}

	/**
	 * Returns all countries whose capital lies within the tropical zone, defined as
	 * latitude between &minus;23.5&deg; and +23.5&deg; (approximately the Tropics
	 * of Cancer and Capricorn).
	 *
	 * @return list of tropical countries sorted alphabetically by name
	 */
	public List<Country> tropical()
	{
		return this.gigaMap.query(this.locationIndex.latitudeBetween(-23.5, 23.5))
			.stream()
			.sorted(Comparator.comparing(Country::name))
			.toList();
	}

	/**
	 * Calculates the haversine (great-circle) distance in kilometres between the
	 * capitals of two countries, rounded to one decimal place.
	 *
	 * @param fromAlpha2 ISO&nbsp;3166-1 alpha-2 code of the first country
	 * @param toAlpha2   ISO&nbsp;3166-1 alpha-2 code of the second country
	 * @return a {@link DistanceResult} containing both countries and the computed
	 *         distance, or {@code null} if either country code is invalid
	 */
	public DistanceResult distance(final String fromAlpha2, final String toAlpha2)
	{
		final Country from = this.findByAlpha2(fromAlpha2);
		final Country to   = this.findByAlpha2(toAlpha2);
		if(from == null || to == null)
		{
			return null;
		}
		final double km = Math.round(
			SpatialIndexer.haversineDistance(from.latitude(), from.longitude(), to.latitude(), to.longitude()) * 10.0
		) / 10.0;
		return new DistanceResult(from, to, km);
	}

	/**
	 * Loads the list of countries from the {@code countries.json} classpath resource
	 * using Jackson deserialization.
	 *
	 * @return the deserialized list of {@link Country} records
	 * @throws RuntimeException if the resource cannot be read or parsed
	 */
	private static List<Country> loadCountries()
	{
		try(final InputStream is = new ClassPathResource("countries.json").getInputStream())
		{
			return new ObjectMapper().readValue(is, new TypeReference<>() {});
		}
		catch(final IOException e)
		{
			throw new RuntimeException("Failed to load countries.json", e);
		}
	}

	/**
	 * Result record pairing a {@link Country} with its haversine distance (in km)
	 * from a reference point or origin country.
	 *
	 * @param country    the neighbouring country
	 * @param distanceKm the distance in kilometres, rounded to one decimal place
	 */
	public record NeighbourResult(Country country, double distanceKm) {}

	/**
	 * Result record representing the haversine distance between two countries.
	 *
	 * @param from       the origin country
	 * @param to         the destination country
	 * @param distanceKm the great-circle distance in kilometres, rounded to one decimal place
	 */
	public record DistanceResult(Country from, Country to, double distanceKm) {}

	/**
	 * Paginated result wrapper returned by {@link #list(int, int)}.
	 *
	 * @param countries the countries on the requested page
	 * @param total     total number of countries in the dataset
	 * @param page      the zero-based page index that was requested
	 * @param size      the page size that was requested
	 */
	public record PageResult(List<Country> countries, long total, int page, int size) {}
}
