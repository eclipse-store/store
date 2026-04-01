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
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.store.demo.vinoteca.dto.PageResult;
import org.eclipse.store.demo.vinoteca.dto.ReviewInput;
import org.eclipse.store.demo.vinoteca.dto.SimilarWineResult;
import org.eclipse.store.demo.vinoteca.dto.WineInput;
import org.eclipse.store.demo.vinoteca.dto.WineStatsResult;
import org.eclipse.store.demo.vinoteca.model.Review;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.demo.vinoteca.service.WineService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class WineGraphqlController
{
	private final WineService wineService;

	public WineGraphqlController(final WineService wineService)
	{
		this.wineService = wineService;
	}

	@QueryMapping
	public Map<String, Object> wines(@Argument final int page, @Argument final int size)
	{
		final PageResult<Wine> result = this.wineService.list(page, size);
		return Map.of(
			"content", result.content(),
			"total",   result.total(),
			"page",    result.page(),
			"size",    result.size()
		);
	}

	@QueryMapping
	public Wine wine(@Argument final long id)
	{
		return this.wineService.findById(id);
	}

	@QueryMapping
	public List<Wine> winesByType(@Argument final String type)
	{
		return this.wineService.byType(type);
	}

	@QueryMapping
	public List<Wine> winesByCountry(@Argument final String country)
	{
		return this.wineService.byCountry(country);
	}

	@QueryMapping
	public List<Wine> winesByRegion(@Argument final String region)
	{
		return this.wineService.byRegion(region);
	}

	@QueryMapping
	public List<Wine> winesByGrape(@Argument final String grape)
	{
		return this.wineService.byGrape(grape);
	}

	@QueryMapping
	public List<Wine> winesByVintage(@Argument final int year)
	{
		return this.wineService.byVintage(year);
	}

	@QueryMapping
	public List<Wine> winesByWinery(@Argument final String wineryName)
	{
		return this.wineService.byWinery(wineryName);
	}

	@QueryMapping
	public List<Wine> topRatedWines(@Argument final int limit)
	{
		return this.wineService.topRated(limit);
	}

	@QueryMapping
	public List<Wine> fulltextSearch(@Argument final String query, @Argument final int maxResults)
	{
		return this.wineService.fulltextSearch(query, maxResults);
	}

	@QueryMapping
	public List<SimilarWineResult> similarWines(@Argument final String query, @Argument final int k)
	{
		return this.wineService.similar(query, k);
	}

	@QueryMapping
	public List<Review> wineReviews(@Argument final long wineId)
	{
		return this.wineService.getReviews(wineId);
	}

	@QueryMapping
	public Map<String, Object> wineStats()
	{
		final WineStatsResult stats = this.wineService.getStats();
		return Map.of(
			"totalCount",          stats.totalCount(),
			"averageRating",       stats.averageRating(),
			"averagePrice",        stats.averagePrice(),
			"typeDistribution",    stats.typeDistribution().entrySet().stream()
				.map(e -> Map.of("type", e.getKey(), "count", e.getValue()))
				.collect(Collectors.toList()),
			"countryDistribution", stats.countryDistribution().entrySet().stream()
				.map(e -> Map.of("country", e.getKey(), "count", e.getValue()))
				.collect(Collectors.toList())
		);
	}

	@MutationMapping
	public Wine createWine(@Argument final WineInput input)
	{
		return this.wineService.create(input);
	}

	@MutationMapping
	public Wine updateWine(@Argument final long id, @Argument final WineInput input)
	{
		return this.wineService.update(id, input);
	}

	@MutationMapping
	public boolean deleteWine(@Argument final long id)
	{
		return this.wineService.delete(id);
	}

	@MutationMapping
	public Wine addReview(@Argument final long wineId, @Argument final ReviewInput input)
	{
		return this.wineService.addReview(wineId, input);
	}
}
