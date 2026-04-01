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

import org.eclipse.store.demo.vinoteca.dto.PageResult;
import org.eclipse.store.demo.vinoteca.dto.ReviewInput;
import org.eclipse.store.demo.vinoteca.dto.SimilarWineResult;
import org.eclipse.store.demo.vinoteca.dto.WineInput;
import org.eclipse.store.demo.vinoteca.dto.WineStatsResult;
import org.eclipse.store.demo.vinoteca.model.Review;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.demo.vinoteca.service.WineService;
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
@RequestMapping("/api/v1/wines")
public class WineRestController
{
	private final WineService wineService;

	public WineRestController(final WineService wineService)
	{
		this.wineService = wineService;
	}

	@GetMapping
	public PageResult<Wine> list(
		@RequestParam(defaultValue = "0") final int page,
		@RequestParam(defaultValue = "20") final int size
	)
	{
		return this.wineService.list(page, size);
	}

	@GetMapping("/{id}")
	public ResponseEntity<Wine> findById(@PathVariable final long id)
	{
		final Wine wine = this.wineService.findById(id);
		return wine != null ? ResponseEntity.ok(wine) : ResponseEntity.notFound().build();
	}

	@PostMapping
	public Wine create(@RequestBody final WineInput input)
	{
		return this.wineService.create(input);
	}

	@PutMapping("/{id}")
	public ResponseEntity<Wine> update(@PathVariable final long id, @RequestBody final WineInput input)
	{
		final Wine wine = this.wineService.update(id, input);
		return wine != null ? ResponseEntity.ok(wine) : ResponseEntity.notFound().build();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable final long id)
	{
		return this.wineService.delete(id)
			? ResponseEntity.noContent().build()
			: ResponseEntity.notFound().build();
	}

	@GetMapping("/by-type/{type}")
	public List<Wine> byType(@PathVariable final String type)
	{
		return this.wineService.byType(type);
	}

	@GetMapping("/by-country/{country}")
	public List<Wine> byCountry(@PathVariable final String country)
	{
		return this.wineService.byCountry(country);
	}

	@GetMapping("/by-region/{region}")
	public List<Wine> byRegion(@PathVariable final String region)
	{
		return this.wineService.byRegion(region);
	}

	@GetMapping("/by-grape/{grape}")
	public List<Wine> byGrape(@PathVariable final String grape)
	{
		return this.wineService.byGrape(grape);
	}

	@GetMapping("/by-winery/{winery}")
	public List<Wine> byWinery(@PathVariable final String winery)
	{
		return this.wineService.byWinery(winery);
	}

	@GetMapping("/by-vintage/{year}")
	public List<Wine> byVintage(@PathVariable final int year)
	{
		return this.wineService.byVintage(year);
	}

	@GetMapping("/top-rated")
	public List<Wine> topRated(@RequestParam(defaultValue = "10") final int limit)
	{
		return this.wineService.topRated(limit);
	}

	@GetMapping("/price-range")
	public List<Wine> priceRange(
		@RequestParam final double minPrice,
		@RequestParam final double maxPrice
	)
	{
		return this.wineService.priceRange(minPrice, maxPrice);
	}

	@GetMapping("/fulltext")
	public List<Wine> fulltextSearch(
		@RequestParam final String q,
		@RequestParam(defaultValue = "20") final int maxResults
	)
	{
		return this.wineService.fulltextSearch(q, maxResults);
	}

	@GetMapping("/similar")
	public List<SimilarWineResult> similar(
		@RequestParam final String query,
		@RequestParam(defaultValue = "5") final int k
	)
	{
		return this.wineService.similar(query, k);
	}

	@GetMapping("/similar/{id}")
	public List<SimilarWineResult> similarTo(
		@PathVariable final long id,
		@RequestParam(defaultValue = "5") final int k
	)
	{
		return this.wineService.similarTo(id, k);
	}

	@PostMapping("/{id}/reviews")
	public Wine addReview(@PathVariable final long id, @RequestBody final ReviewInput input)
	{
		return this.wineService.addReview(id, input);
	}

	@GetMapping("/{id}/reviews")
	public List<Review> getReviews(@PathVariable final long id)
	{
		return this.wineService.getReviews(id);
	}

	@GetMapping("/stats")
	public WineStatsResult getStats()
	{
		return this.wineService.getStats();
	}
}
