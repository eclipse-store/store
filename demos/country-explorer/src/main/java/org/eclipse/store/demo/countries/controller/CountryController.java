package org.eclipse.store.demo.countries.controller;

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

import org.eclipse.store.demo.countries.model.Country;
import org.eclipse.store.demo.countries.service.CountryService;
import org.eclipse.store.demo.countries.service.CountryService.DistanceResult;
import org.eclipse.store.demo.countries.service.CountryService.NeighbourResult;
import org.eclipse.store.demo.countries.service.CountryService.PageResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller that exposes country exploration endpoints under {@code /countries}.
 * <p>
 * All heavy lifting is delegated to {@link CountryService}; this controller is a thin
 * HTTP adapter that maps request parameters to service calls and translates results
 * into appropriate HTTP responses.
 *
 * <h2>Endpoints overview</h2>
 * <table>
 *   <tr><th>Verb</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>{@code /countries}</td><td>Paginated list of all countries</td></tr>
 *   <tr><td>GET</td><td>{@code /countries/{alpha2}}</td><td>Single country by ISO&nbsp;3166-1 alpha-2 code</td></tr>
 *   <tr><td>GET</td><td>{@code /countries/nearby}</td><td>Countries within a radius of another country</td></tr>
 *   <tr><td>GET</td><td>{@code /countries/near}</td><td>Countries within a radius of a coordinate pair</td></tr>
 *   <tr><td>GET</td><td>{@code /countries/nearest}</td><td>K-nearest neighbours of a country</td></tr>
 *   <tr><td>GET</td><td>{@code /countries/continent/{name}}</td><td>All countries on a continent</td></tr>
 *   <tr><td>GET</td><td>{@code /countries/within-box}</td><td>Countries inside a lat/lon bounding box</td></tr>
 *   <tr><td>GET</td><td>{@code /countries/hemisphere}</td><td>Countries in a given hemisphere</td></tr>
 *   <tr><td>GET</td><td>{@code /countries/tropical}</td><td>Countries in the tropical zone</td></tr>
 *   <tr><td>GET</td><td>{@code /countries/distance}</td><td>Haversine distance between two countries</td></tr>
 * </table>
 *
 * @see CountryService
 */
@RestController
@RequestMapping("/countries")
public class CountryController
{
	private final CountryService countryService;

	/**
	 * Constructs the controller with the required service dependency.
	 *
	 * @param countryService the service that provides country query operations
	 */
	public CountryController(final CountryService countryService)
	{
		this.countryService = countryService;
	}

	/**
	 * Returns a paginated list of all countries, sorted alphabetically by name.
	 *
	 * @param page zero-based page index (default {@code 0})
	 * @param size number of countries per page (default {@code 20})
	 * @return a {@link PageResult} containing the requested page of countries together
	 *         with pagination metadata
	 */
	@GetMapping
	public PageResult list(
		@RequestParam(defaultValue = "0") final int page,
		@RequestParam(defaultValue = "20") final int size
	)
	{
		return this.countryService.list(page, size);
	}

	/**
	 * Looks up a single country by its ISO&nbsp;3166-1 alpha-2 code.
	 *
	 * @param alpha2 the two-letter country code (case-insensitive)
	 * @return {@code 200 OK} with the {@link Country} if found, or {@code 404 Not Found}
	 */
	@GetMapping("/{alpha2}")
	public ResponseEntity<Country> findByAlpha2(@PathVariable final String alpha2)
	{
		final Country country = this.countryService.findByAlpha2(alpha2);
		return country != null
			? ResponseEntity.ok(country)
			: ResponseEntity.notFound().build();
	}

	/**
	 * Finds all countries whose capital coordinates lie within a given radius of the
	 * country identified by {@code alpha2}. The origin country itself is excluded from
	 * the results.
	 *
	 * @param alpha2  ISO&nbsp;3166-1 alpha-2 code of the origin country
	 * @param radiusKm search radius in kilometres (default {@code 1000})
	 * @return a list of {@link NeighbourResult} entries sorted by ascending distance,
	 *         or an empty list if the origin country is not found
	 */
	@GetMapping("/nearby")
	public List<NeighbourResult> nearby(
		@RequestParam final String alpha2,
		@RequestParam(defaultValue = "1000") final double radiusKm
	)
	{
		return this.countryService.nearby(alpha2, radiusKm);
	}

	/**
	 * Finds all countries whose capital coordinates lie within a given radius of an
	 * arbitrary geographic point.
	 *
	 * @param lat      latitude of the search centre in decimal degrees
	 * @param lon      longitude of the search centre in decimal degrees
	 * @param radiusKm search radius in kilometres (default {@code 1000})
	 * @return a list of {@link NeighbourResult} entries sorted by ascending distance
	 */
	@GetMapping("/near")
	public List<NeighbourResult> nearCoordinates(
		@RequestParam final double lat,
		@RequestParam final double lon,
		@RequestParam(defaultValue = "1000") final double radiusKm
	)
	{
		return this.countryService.nearCoordinates(lat, lon, radiusKm);
	}

	/**
	 * Returns the {@code k} nearest countries to the country identified by
	 * {@code alpha2}, based on haversine distance between capital coordinates.
	 *
	 * @param alpha2 ISO&nbsp;3166-1 alpha-2 code of the origin country
	 * @param k      maximum number of neighbours to return (default {@code 5})
	 * @return a list of up to {@code k} {@link NeighbourResult} entries sorted by
	 *         ascending distance, or an empty list if the origin is not found
	 */
	@GetMapping("/nearest")
	public List<NeighbourResult> kNearest(
		@RequestParam final String alpha2,
		@RequestParam(defaultValue = "5") final int k
	)
	{
		return this.countryService.kNearest(alpha2, k);
	}

	/**
	 * Returns all countries belonging to the specified continent, sorted
	 * alphabetically by name.
	 *
	 * @param name continent name (e.g. {@code "Europe"}, {@code "Asia"})
	 * @return list of matching countries, or an empty list if the continent name
	 *         does not match any entry
	 */
	@GetMapping("/continent/{name}")
	public List<Country> continent(@PathVariable final String name)
	{
		return this.countryService.continent(name);
	}

	/**
	 * Returns all countries whose capital coordinates fall inside the specified
	 * latitude/longitude bounding box, sorted alphabetically by name.
	 *
	 * @param minLat southern boundary in decimal degrees
	 * @param maxLat northern boundary in decimal degrees
	 * @param minLon western boundary in decimal degrees
	 * @param maxLon eastern boundary in decimal degrees
	 * @return list of countries within the bounding box
	 */
	@GetMapping("/within-box")
	public List<Country> withinBox(
		@RequestParam final double minLat,
		@RequestParam final double maxLat,
		@RequestParam final double minLon,
		@RequestParam final double maxLon
	)
	{
		return this.countryService.withinBox(minLat, maxLat, minLon, maxLon);
	}

	/**
	 * Returns all countries located in the specified hemisphere, sorted
	 * alphabetically by name.
	 *
	 * @param hemisphere one of {@code "north"}, {@code "south"}, {@code "east"},
	 *                   or {@code "west"} (case-insensitive)
	 * @return list of matching countries, or an empty list for an unrecognised
	 *         hemisphere value
	 */
	@GetMapping("/hemisphere")
	public List<Country> hemisphere(@RequestParam final String hemisphere)
	{
		return this.countryService.hemisphere(hemisphere);
	}

	/**
	 * Returns all countries whose capital lies within the tropical zone
	 * (latitude between &minus;23.5&deg; and +23.5&deg;), sorted alphabetically
	 * by name.
	 *
	 * @return list of tropical countries
	 */
	@GetMapping("/tropical")
	public List<Country> tropical()
	{
		return this.countryService.tropical();
	}

	/**
	 * Calculates the haversine (great-circle) distance in kilometres between the
	 * capitals of two countries.
	 *
	 * @param from ISO&nbsp;3166-1 alpha-2 code of the first country
	 * @param to   ISO&nbsp;3166-1 alpha-2 code of the second country
	 * @return {@code 200 OK} with a {@link DistanceResult} if both countries are found,
	 *         or {@code 404 Not Found} if either code is invalid
	 */
	@GetMapping("/distance")
	public ResponseEntity<DistanceResult> distance(
		@RequestParam final String from,
		@RequestParam final String to
	)
	{
		final DistanceResult result = this.countryService.distance(from, to);
		return result != null
			? ResponseEntity.ok(result)
			: ResponseEntity.notFound().build();
	}
}
