package org.eclipse.store.demo.vinoteca.controller;

/*-
 * #%L
 * EclipseStore Demo Vinoteca
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

import java.util.List;

import org.eclipse.store.demo.vinoteca.dto.NearbyWineryResult;
import org.eclipse.store.demo.vinoteca.dto.PageResult;
import org.eclipse.store.demo.vinoteca.dto.WineryInput;
import org.eclipse.store.demo.vinoteca.model.Winery;
import org.eclipse.store.demo.vinoteca.service.WineryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing winery operations at {@code /api/v1/wineries}.
 * <p>
 * Most of the endpoints exercise the spatial index registered on the wineries GigaMap (proximity,
 * bounding-box, hemisphere, k-nearest, distance). The controller is a thin pass-through over
 * {@link WineryService}.
 */
@RestController
@RequestMapping("/api/v1/wineries")
public class WineryRestController
{
	private final WineryService wineryService;

	/**
	 * @param wineryService the underlying winery application service
	 */
	public WineryRestController(final WineryService wineryService)
	{
		this.wineryService = wineryService;
	}

	/**
	 * {@code GET /api/v1/wineries} — paged listing of all wineries.
	 *
	 * @param page zero-based page number (default {@code 0})
	 * @param size page size (default {@code 20})
	 * @return a page of wineries
	 */
	@GetMapping
	public PageResult<Winery> list(
		@RequestParam(defaultValue = "0") final int page,
		@RequestParam(defaultValue = "20") final int size
	)
	{
		return this.wineryService.list(page, size);
	}

	/**
	 * {@code GET /api/v1/wineries/{id}} — single-winery lookup by GigaMap entity id.
	 *
	 * @param id the winery id
	 * @return 200 with the winery, or 404 if no winery with that id exists
	 */
	@GetMapping("/{id}")
	public ResponseEntity<Winery> findById(@PathVariable final long id)
	{
		final Winery winery = this.wineryService.findById(id);
		return winery != null ? ResponseEntity.ok(winery) : ResponseEntity.notFound().build();
	}

	/**
	 * {@code POST /api/v1/wineries} — create a new winery.
	 *
	 * @param input the winery to create
	 * @return the created winery
	 */
	@PostMapping
	public Winery create(@RequestBody final WineryInput input)
	{
		return this.wineryService.create(input);
	}

	/**
	 * {@code PUT /api/v1/wineries/{id}} — partial update of an existing winery.
	 *
	 * @param id    the winery id
	 * @param input the (partial) new field values
	 * @return 200 with the updated winery, or 404 if no winery with that id exists
	 */
	@PutMapping("/{id}")
	public ResponseEntity<Winery> update(@PathVariable final long id, @RequestBody final WineryInput input)
	{
		final Winery winery = this.wineryService.update(id, input);
		return winery != null ? ResponseEntity.ok(winery) : ResponseEntity.notFound().build();
	}

	/**
	 * {@code DELETE /api/v1/wineries/{id}} — delete a winery.
	 *
	 * @param id the winery id
	 * @return 204 if deleted, 404 if no winery with that id exists
	 */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable final long id)
	{
		return this.wineryService.delete(id)
			? ResponseEntity.noContent().build()
			: ResponseEntity.notFound().build();
	}

	/**
	 * {@code GET /api/v1/wineries/nearby} — wineries within a radius of an arbitrary point.
	 *
	 * @param lat      query latitude in decimal degrees
	 * @param lon      query longitude in decimal degrees
	 * @param radiusKm search radius in kilometres (default {@code 100})
	 * @return matching wineries with their distance from the query point
	 */
	@GetMapping("/nearby")
	public List<NearbyWineryResult> nearby(
		@RequestParam final double lat,
		@RequestParam final double lon,
		@RequestParam(defaultValue = "100") final double radiusKm
	)
	{
		return this.wineryService.nearby(lat, lon, radiusKm);
	}

	/**
	 * {@code GET /api/v1/wineries/nearby/{id}} — wineries within a radius of another winery.
	 *
	 * @param id       the anchor winery id
	 * @param radiusKm search radius in kilometres (default {@code 100})
	 * @return matching wineries (excluding the anchor) with their distance
	 */
	@GetMapping("/nearby/{id}")
	public List<NearbyWineryResult> nearbyWinery(
		@PathVariable final long id,
		@RequestParam(defaultValue = "100") final double radiusKm
	)
	{
		return this.wineryService.nearbyWinery(id, radiusKm);
	}

	/**
	 * {@code GET /api/v1/wineries/within-box} — wineries inside a latitude/longitude bounding box.
	 *
	 * @param minLat minimum latitude (inclusive)
	 * @param maxLat maximum latitude (inclusive)
	 * @param minLon minimum longitude (inclusive)
	 * @param maxLon maximum longitude (inclusive)
	 * @return matching wineries
	 */
	@GetMapping("/within-box")
	public List<Winery> withinBox(
		@RequestParam final double minLat,
		@RequestParam final double maxLat,
		@RequestParam final double minLon,
		@RequestParam final double maxLon
	)
	{
		return this.wineryService.withinBox(minLat, maxLat, minLon, maxLon);
	}

	/**
	 * {@code GET /api/v1/wineries/hemisphere/{name}} — wineries on a given hemisphere
	 * ({@code north}, {@code south}, {@code east} or {@code west}).
	 *
	 * @param name the hemisphere name (case-insensitive)
	 * @return matching wineries; an empty list for unknown hemisphere names
	 */
	@GetMapping("/hemisphere/{name}")
	public List<Winery> hemisphere(@PathVariable final String name)
	{
		return this.wineryService.hemisphere(name);
	}

	/**
	 * {@code GET /api/v1/wineries/nearest/{id}} — k nearest wineries to an anchor.
	 *
	 * @param id the anchor winery id
	 * @param k  the number of neighbours to return (default {@code 5})
	 * @return the k nearest wineries with their distances
	 */
	@GetMapping("/nearest/{id}")
	public List<NearbyWineryResult> kNearest(
		@PathVariable final long id,
		@RequestParam(defaultValue = "5") final int k
	)
	{
		return this.wineryService.kNearest(id, k);
	}

	/**
	 * {@code GET /api/v1/wineries/distance} — Haversine distance between two wineries.
	 *
	 * @param from the first winery id
	 * @param to   the second winery id
	 * @return 200 with the distance in kilometres, or 404 if either winery is unknown
	 */
	@GetMapping("/distance")
	public ResponseEntity<Double> distance(
		@RequestParam final long from,
		@RequestParam final long to
	)
	{
		final Double dist = this.wineryService.distance(from, to);
		return dist != null ? ResponseEntity.ok(dist) : ResponseEntity.notFound().build();
	}

	/**
	 * {@code GET /api/v1/wineries/by-country/{country}} — wineries in a given country
	 * (bitmap-index lookup).
	 *
	 * @param country the country
	 * @return matching wineries
	 */
	@GetMapping("/by-country/{country}")
	public List<Winery> byCountry(@PathVariable final String country)
	{
		return this.wineryService.byCountry(country);
	}

	/**
	 * {@code GET /api/v1/wineries/by-region/{region}} — wineries in a given region
	 * (bitmap-index lookup).
	 *
	 * @param region the region
	 * @return matching wineries
	 */
	@GetMapping("/by-region/{region}")
	public List<Winery> byRegion(@PathVariable final String region)
	{
		return this.wineryService.byRegion(region);
	}
}
