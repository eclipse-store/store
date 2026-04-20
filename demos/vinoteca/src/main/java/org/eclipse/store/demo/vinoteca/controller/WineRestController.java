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
import org.eclipse.store.demo.vinoteca.model.GrapeVariety;
import org.eclipse.store.demo.vinoteca.model.Review;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.demo.vinoteca.model.WineType;
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

/**
 * REST controller exposing the wine catalogue at {@code /api/v1/wines}.
 * <p>
 * The controller is a thin pass-through over {@link WineService} — it adapts HTTP requests to
 * service calls and translates {@code null} returns into 404 responses where appropriate. All
 * business logic, indexing and persistence concerns live on the service.
 */
@RestController
@RequestMapping("/api/v1/wines")
public class WineRestController
{
	private final WineService wineService;

	/**
	 * @param wineService the underlying wine application service
	 */
	public WineRestController(final WineService wineService)
	{
		this.wineService = wineService;
	}

	/**
	 * {@code GET /api/v1/wines} — paged listing of wines with optional filters.
	 * <p>
	 * When no filter parameters are supplied this returns a simple paged listing of all wines.
	 * When one or more filters are present the bitmap indices are combined into a single
	 * {@link org.eclipse.store.gigamap.types.GigaQuery GigaQuery} (via
	 * {@link WineService#filter}) and the result is paged.
	 *
	 * @param page    zero-based page number (default {@code 0})
	 * @param size    page size (default {@code 20})
	 * @param name    substring filter on wine name (case-insensitive), or {@code null} to skip
	 * @param type    wine type filter (e.g. {@code RED}), or {@code null} to skip
	 * @param grape   grape variety filter (e.g. {@code MERLOT}), or {@code null} to skip
	 * @param vintage vintage year filter, or {@code null} to skip
	 * @param country country filter, or {@code null} to skip
	 * @param region  region filter, or {@code null} to skip
	 * @return a page of (filtered) wines
	 */
	@GetMapping
	public PageResult<Wine> list(
		@RequestParam(defaultValue = "0") final int page,
		@RequestParam(defaultValue = "20") final int size,
		@RequestParam(required = false) final String name,
		@RequestParam(required = false) final WineType type,
		@RequestParam(required = false) final GrapeVariety grape,
		@RequestParam(required = false) final Integer vintage,
		@RequestParam(required = false) final String country,
		@RequestParam(required = false) final String region
	)
	{
		if (name == null && type == null && grape == null && vintage == null && country == null && region == null)
		{
			return this.wineService.list(page, size);
		}
		return this.wineService.filter(name, type, grape, vintage, country, region, page, size);
	}

	/**
	 * {@code GET /api/v1/wines/{id}} — single-wine lookup by GigaMap entity id.
	 *
	 * @param id the wine id
	 * @return 200 with the wine, or 404 if no wine with that id exists
	 */
	@GetMapping("/{id}")
	public ResponseEntity<Wine> findById(@PathVariable final long id)
	{
		final Wine wine = this.wineService.findById(id);
		return wine != null ? ResponseEntity.ok(wine) : ResponseEntity.notFound().build();
	}

	/**
	 * {@code POST /api/v1/wines} — create a new wine.
	 *
	 * @param input the wine to create
	 * @return the created wine
	 */
	@PostMapping
	public Wine create(@RequestBody final WineInput input)
	{
		return this.wineService.create(input);
	}

	/**
	 * {@code PUT /api/v1/wines/{id}} — partial update of an existing wine.
	 *
	 * @param id    the wine id
	 * @param input the (partial) new field values
	 * @return 200 with the updated wine, or 404 if no wine with that id exists
	 */
	@PutMapping("/{id}")
	public ResponseEntity<Wine> update(@PathVariable final long id, @RequestBody final WineInput input)
	{
		final Wine wine = this.wineService.update(id, input);
		return wine != null ? ResponseEntity.ok(wine) : ResponseEntity.notFound().build();
	}

	/**
	 * {@code DELETE /api/v1/wines/{id}} — delete a wine.
	 *
	 * @param id the wine id
	 * @return 204 if deleted, 404 if no wine with that id exists
	 */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable final long id)
	{
		return this.wineService.delete(id)
			? ResponseEntity.noContent().build()
			: ResponseEntity.notFound().build();
	}

	/**
	 * {@code GET /api/v1/wines/by-type/{type}} — wines of a given {@link org.eclipse.store.demo.vinoteca.model.WineType WineType}
	 * (bitmap-index lookup).
	 *
	 * @param type the wine type
	 * @return matching wines
	 */
	@GetMapping("/by-type/{type}")
	public List<Wine> byType(@PathVariable final WineType type)
	{
		return this.wineService.byType(type);
	}

	/**
	 * {@code GET /api/v1/wines/by-country/{country}} — wines from a given country (bitmap-index
	 * lookup).
	 *
	 * @param country the country
	 * @return matching wines
	 */
	@GetMapping("/by-country/{country}")
	public List<Wine> byCountry(@PathVariable final String country)
	{
		return this.wineService.byCountry(country);
	}

	/**
	 * {@code GET /api/v1/wines/by-region/{region}} — wines from a given region (bitmap-index
	 * lookup).
	 *
	 * @param region the region
	 * @return matching wines
	 */
	@GetMapping("/by-region/{region}")
	public List<Wine> byRegion(@PathVariable final String region)
	{
		return this.wineService.byRegion(region);
	}

	/**
	 * {@code GET /api/v1/wines/by-grape/{grape}} — wines made from a given grape variety
	 * (bitmap-index lookup).
	 *
	 * @param grape the grape variety enum
	 * @return matching wines
	 */
	@GetMapping("/by-grape/{grape}")
	public List<Wine> byGrape(@PathVariable final GrapeVariety grape)
	{
		return this.wineService.byGrape(grape);
	}

	/**
	 * {@code GET /api/v1/wines/by-winery/{winery}} — wines produced by a given winery
	 * (bitmap-index lookup).
	 *
	 * @param winery the winery name
	 * @return matching wines
	 */
	@GetMapping("/by-winery/{winery}")
	public List<Wine> byWinery(@PathVariable final String winery)
	{
		return this.wineService.byWinery(winery);
	}

	/**
	 * {@code GET /api/v1/wines/by-vintage/{year}} — wines of a given vintage year.
	 *
	 * @param year the vintage year
	 * @return matching wines
	 */
	@GetMapping("/by-vintage/{year}")
	public List<Wine> byVintage(@PathVariable final int year)
	{
		return this.wineService.byVintage(year);
	}

	/**
	 * {@code GET /api/v1/wines/top-rated} — highest-rated wines, descending by rating.
	 *
	 * @param limit the maximum number of wines to return (default {@code 10})
	 * @return top-rated wines
	 */
	@GetMapping("/top-rated")
	public List<Wine> topRated(@RequestParam(defaultValue = "10") final int limit)
	{
		return this.wineService.topRated(limit);
	}

	/**
	 * {@code GET /api/v1/wines/price-range} — wines whose price falls inside the given closed
	 * interval, sorted ascending by price.
	 *
	 * @param minPrice inclusive lower price bound
	 * @param maxPrice inclusive upper price bound
	 * @return matching wines
	 */
	@GetMapping("/price-range")
	public List<Wine> priceRange(
		@RequestParam final double minPrice,
		@RequestParam final double maxPrice
	)
	{
		return this.wineService.priceRange(minPrice, maxPrice);
	}

	/**
	 * {@code GET /api/v1/wines/fulltext} — Lucene full-text search over the wine name, tasting
	 * notes, aroma and food pairing.
	 *
	 * @param q          the Lucene query string
	 * @param maxResults the maximum number of hits to return (default {@code 20})
	 * @return matching wines, in Lucene relevance order
	 */
	@GetMapping("/fulltext")
	public List<Wine> fulltextSearch(
		@RequestParam final String q,
		@RequestParam(defaultValue = "20") final int maxResults
	)
	{
		return this.wineService.fulltextSearch(q, maxResults);
	}

	/**
	 * {@code GET /api/v1/wines/similar} — vector similarity search from a free-form query.
	 *
	 * @param query the natural-language query
	 * @param k     the number of nearest neighbours to return (default {@code 5})
	 * @return matching wines with their cosine similarity scores
	 */
	@GetMapping("/similar")
	public List<SimilarWineResult> similar(
		@RequestParam final String query,
		@RequestParam(defaultValue = "50") final int k
	)
	{
		return this.wineService.similar(query, k);
	}

	/**
	 * {@code GET /api/v1/wines/similar/{id}} — vector similarity search anchored on an existing
	 * wine.
	 *
	 * @param id the anchor wine id
	 * @param k  the number of similar wines to return (default {@code 5})
	 * @return similar wines (excluding the anchor) with their cosine similarity scores
	 */
	@GetMapping("/similar/{id}")
	public List<SimilarWineResult> similarTo(
		@PathVariable final long id,
		@RequestParam(defaultValue = "50") final int k
	)
	{
		return this.wineService.similarTo(id, k);
	}

	/**
	 * {@code POST /api/v1/wines/{id}/reviews} — attach a review to a wine.
	 *
	 * @param id    the wine id
	 * @param input the review payload
	 * @return the updated wine (with the new review and refreshed average rating)
	 */
	@PostMapping("/{id}/reviews")
	public Wine addReview(@PathVariable final long id, @RequestBody final ReviewInput input)
	{
		return this.wineService.addReview(id, input);
	}

	/**
	 * {@code GET /api/v1/wines/{id}/reviews} — list the reviews of a wine.
	 *
	 * @param id the wine id
	 * @return the (possibly empty) list of reviews
	 */
	@GetMapping("/{id}/reviews")
	public List<Review> getReviews(@PathVariable final long id)
	{
		return this.wineService.getReviews(id);
	}

	/**
	 * {@code GET /api/v1/wines/stats} — aggregated catalog statistics.
	 *
	 * @return the catalog statistics
	 */
	@GetMapping("/stats")
	public WineStatsResult getStats()
	{
		return this.wineService.getStats();
	}
}
