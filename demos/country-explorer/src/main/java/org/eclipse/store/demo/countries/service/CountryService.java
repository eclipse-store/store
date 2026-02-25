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

@Service
public class CountryService
{
	private static final Logger LOG = LoggerFactory.getLogger(CountryService.class);

	private final GigaMap<Country> gigaMap;
	private final LocationIndex locationIndex;

	public CountryService(
		final GigaMap<Country> gigaMap,
		final LocationIndex locationIndex
	)
	{
		this.gigaMap       = gigaMap;
		this.locationIndex = locationIndex;
	}

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

	public Country findByAlpha2(final String alpha2)
	{
		return this.gigaMap.query(CountryIndices.ALPHA2.is(alpha2.toUpperCase()))
			.findFirst()
			.orElse(null);
	}

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

	public List<Country> continent(final String name)
	{
		return this.gigaMap.query(CountryIndices.CONTINENT.is(name))
			.stream()
			.sorted(Comparator.comparing(Country::name))
			.toList();
	}

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

	public List<Country> tropical()
	{
		return this.gigaMap.query(this.locationIndex.latitudeBetween(-23.5, 23.5))
			.stream()
			.sorted(Comparator.comparing(Country::name))
			.toList();
	}

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

	public record NeighbourResult(Country country, double distanceKm) {}

	public record DistanceResult(Country from, Country to, double distanceKm) {}

	public record PageResult(List<Country> countries, long total, int page, int size) {}
}
