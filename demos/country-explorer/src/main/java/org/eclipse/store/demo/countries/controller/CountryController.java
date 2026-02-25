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

@RestController
@RequestMapping("/countries")
public class CountryController
{
	private final CountryService countryService;

	public CountryController(final CountryService countryService)
	{
		this.countryService = countryService;
	}

	@GetMapping
	public PageResult list(
		@RequestParam(defaultValue = "0") final int page,
		@RequestParam(defaultValue = "20") final int size
	)
	{
		return this.countryService.list(page, size);
	}

	@GetMapping("/{alpha2}")
	public ResponseEntity<Country> findByAlpha2(@PathVariable final String alpha2)
	{
		final Country country = this.countryService.findByAlpha2(alpha2);
		return country != null
			? ResponseEntity.ok(country)
			: ResponseEntity.notFound().build();
	}

	@GetMapping("/nearby")
	public List<NeighbourResult> nearby(
		@RequestParam final String alpha2,
		@RequestParam(defaultValue = "1000") final double radiusKm
	)
	{
		return this.countryService.nearby(alpha2, radiusKm);
	}

	@GetMapping("/near")
	public List<NeighbourResult> nearCoordinates(
		@RequestParam final double lat,
		@RequestParam final double lon,
		@RequestParam(defaultValue = "1000") final double radiusKm
	)
	{
		return this.countryService.nearCoordinates(lat, lon, radiusKm);
	}

	@GetMapping("/nearest")
	public List<NeighbourResult> kNearest(
		@RequestParam final String alpha2,
		@RequestParam(defaultValue = "5") final int k
	)
	{
		return this.countryService.kNearest(alpha2, k);
	}

	@GetMapping("/continent/{name}")
	public List<Country> continent(@PathVariable final String name)
	{
		return this.countryService.continent(name);
	}

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

	@GetMapping("/hemisphere")
	public List<Country> hemisphere(@RequestParam final String hemisphere)
	{
		return this.countryService.hemisphere(hemisphere);
	}

	@GetMapping("/tropical")
	public List<Country> tropical()
	{
		return this.countryService.tropical();
	}

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
