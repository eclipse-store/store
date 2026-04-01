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

@RestController
@RequestMapping("/api/v1/wineries")
public class WineryRestController
{
	private final WineryService wineryService;

	public WineryRestController(final WineryService wineryService)
	{
		this.wineryService = wineryService;
	}

	@GetMapping
	public PageResult<Winery> list(
		@RequestParam(defaultValue = "0") final int page,
		@RequestParam(defaultValue = "20") final int size
	)
	{
		return this.wineryService.list(page, size);
	}

	@GetMapping("/{id}")
	public ResponseEntity<Winery> findById(@PathVariable final long id)
	{
		final Winery winery = this.wineryService.findById(id);
		return winery != null ? ResponseEntity.ok(winery) : ResponseEntity.notFound().build();
	}

	@PostMapping
	public Winery create(@RequestBody final WineryInput input)
	{
		return this.wineryService.create(input);
	}

	@PutMapping("/{id}")
	public ResponseEntity<Winery> update(@PathVariable final long id, @RequestBody final WineryInput input)
	{
		final Winery winery = this.wineryService.update(id, input);
		return winery != null ? ResponseEntity.ok(winery) : ResponseEntity.notFound().build();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable final long id)
	{
		return this.wineryService.delete(id)
			? ResponseEntity.noContent().build()
			: ResponseEntity.notFound().build();
	}

	@GetMapping("/nearby")
	public List<NearbyWineryResult> nearby(
		@RequestParam final double lat,
		@RequestParam final double lon,
		@RequestParam(defaultValue = "100") final double radiusKm
	)
	{
		return this.wineryService.nearby(lat, lon, radiusKm);
	}

	@GetMapping("/nearby/{id}")
	public List<NearbyWineryResult> nearbyWinery(
		@PathVariable final long id,
		@RequestParam(defaultValue = "100") final double radiusKm
	)
	{
		return this.wineryService.nearbyWinery(id, radiusKm);
	}

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

	@GetMapping("/hemisphere/{name}")
	public List<Winery> hemisphere(@PathVariable final String name)
	{
		return this.wineryService.hemisphere(name);
	}

	@GetMapping("/nearest/{id}")
	public List<NearbyWineryResult> kNearest(
		@PathVariable final long id,
		@RequestParam(defaultValue = "5") final int k
	)
	{
		return this.wineryService.kNearest(id, k);
	}

	@GetMapping("/distance")
	public ResponseEntity<Double> distance(
		@RequestParam final long from,
		@RequestParam final long to
	)
	{
		final Double dist = this.wineryService.distance(from, to);
		return dist != null ? ResponseEntity.ok(dist) : ResponseEntity.notFound().build();
	}

	@GetMapping("/by-country/{country}")
	public List<Winery> byCountry(@PathVariable final String country)
	{
		return this.wineryService.byCountry(country);
	}

	@GetMapping("/by-region/{region}")
	public List<Winery> byRegion(@PathVariable final String region)
	{
		return this.wineryService.byRegion(region);
	}
}
