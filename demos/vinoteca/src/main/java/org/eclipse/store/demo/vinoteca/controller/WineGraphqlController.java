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

/**
 * GraphQL controller backing all wine-related queries and mutations declared in
 * {@code schema.graphqls}.
 * <p>
 * The {@code wines}, {@code wineStats} (and similar paged/aggregated) resolvers return a plain
 * {@link Map} rather than the corresponding DTO; this lets the schema expose flattened scalar
 * fields (e.g. {@code typeDistribution: [TypeCount!]}) without forcing an extra DTO layer for the
 * GraphQL boundary. All other resolvers return domain objects directly — derived scalar fields
 * (such as {@code Wine.wineryName} or {@code Wine.price}) are wired up by
 * {@link org.eclipse.store.demo.vinoteca.config.GraphQlConfig GraphQlConfig}.
 */
@Controller
public class WineGraphqlController
{
	private final WineService wineService;

	/**
	 * @param wineService the underlying wine application service
	 */
	public WineGraphqlController(final WineService wineService)
	{
		this.wineService = wineService;
	}

	/**
	 * Resolves the {@code wines} query — a paged listing of wines.
	 *
	 * @param page zero-based page number
	 * @param size page size
	 * @return a map shaped like the GraphQL {@code WinePage} type
	 */
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

	/**
	 * Resolves the {@code wine(id)} query.
	 *
	 * @param id the wine id
	 * @return the wine, or {@code null} if no wine with that id exists
	 */
	@QueryMapping
	public Wine wine(@Argument final long id)
	{
		return this.wineService.findById(id);
	}

	/**
	 * Resolves the {@code winesByType(type)} query (bitmap-index lookup).
	 *
	 * @param type the wine type name
	 * @return matching wines
	 */
	@QueryMapping
	public List<Wine> winesByType(@Argument final String type)
	{
		return this.wineService.byType(type);
	}

	/**
	 * Resolves the {@code winesByCountry(country)} query (bitmap-index lookup).
	 *
	 * @param country the country
	 * @return matching wines
	 */
	@QueryMapping
	public List<Wine> winesByCountry(@Argument final String country)
	{
		return this.wineService.byCountry(country);
	}

	/**
	 * Resolves the {@code winesByRegion(region)} query (bitmap-index lookup).
	 *
	 * @param region the region
	 * @return matching wines
	 */
	@QueryMapping
	public List<Wine> winesByRegion(@Argument final String region)
	{
		return this.wineService.byRegion(region);
	}

	/**
	 * Resolves the {@code winesByGrape(grape)} query (bitmap-index lookup).
	 *
	 * @param grape the grape variety enum name
	 * @return matching wines
	 */
	@QueryMapping
	public List<Wine> winesByGrape(@Argument final String grape)
	{
		return this.wineService.byGrape(grape);
	}

	/**
	 * Resolves the {@code winesByVintage(year)} query.
	 *
	 * @param year the vintage year
	 * @return matching wines
	 */
	@QueryMapping
	public List<Wine> winesByVintage(@Argument final int year)
	{
		return this.wineService.byVintage(year);
	}

	/**
	 * Resolves the {@code winesByWinery(wineryName)} query (bitmap-index lookup).
	 *
	 * @param wineryName the winery name
	 * @return matching wines
	 */
	@QueryMapping
	public List<Wine> winesByWinery(@Argument final String wineryName)
	{
		return this.wineService.byWinery(wineryName);
	}

	/**
	 * Resolves the {@code topRatedWines(limit)} query.
	 *
	 * @param limit the maximum number of wines to return
	 * @return top-rated wines
	 */
	@QueryMapping
	public List<Wine> topRatedWines(@Argument final int limit)
	{
		return this.wineService.topRated(limit);
	}

	/**
	 * Resolves the {@code fulltextSearch} query (Lucene index).
	 *
	 * @param query      the Lucene query string
	 * @param maxResults the maximum number of hits to return
	 * @return matching wines, in Lucene relevance order
	 */
	@QueryMapping
	public List<Wine> fulltextSearch(@Argument final String query, @Argument final int maxResults)
	{
		return this.wineService.fulltextSearch(query, maxResults);
	}

	/**
	 * Resolves the {@code similarWines} query (vector index).
	 *
	 * @param query the natural-language query
	 * @param k     the number of nearest neighbours to return
	 * @return similar wines with their cosine similarity scores
	 */
	@QueryMapping
	public List<SimilarWineResult> similarWines(@Argument final String query, @Argument final int k)
	{
		return this.wineService.similar(query, k);
	}

	/**
	 * Resolves the {@code wineReviews(wineId)} query.
	 *
	 * @param wineId the wine id
	 * @return the (possibly empty) list of reviews
	 */
	@QueryMapping
	public List<Review> wineReviews(@Argument final long wineId)
	{
		return this.wineService.getReviews(wineId);
	}

	/**
	 * Resolves the {@code wineStats} query — aggregated catalog statistics, reshaped into the
	 * GraphQL {@code WineStats} type (which exposes the type/country distributions as lists of
	 * {@code TypeCount}/{@code CountryCount} objects rather than maps).
	 *
	 * @return a map shaped like the GraphQL {@code WineStats} type
	 */
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

	/**
	 * Resolves the {@code createWine} mutation.
	 *
	 * @param input the wine to create
	 * @return the created wine
	 */
	@MutationMapping
	public Wine createWine(@Argument final WineInput input)
	{
		return this.wineService.create(input);
	}

	/**
	 * Resolves the {@code updateWine} mutation (partial update).
	 *
	 * @param id    the wine id
	 * @param input the (partial) new field values
	 * @return the updated wine, or {@code null} if no wine with that id exists
	 */
	@MutationMapping
	public Wine updateWine(@Argument final long id, @Argument final WineInput input)
	{
		return this.wineService.update(id, input);
	}

	/**
	 * Resolves the {@code deleteWine} mutation.
	 *
	 * @param id the wine id
	 * @return {@code true} if a wine was deleted, {@code false} otherwise
	 */
	@MutationMapping
	public boolean deleteWine(@Argument final long id)
	{
		return this.wineService.delete(id);
	}

	/**
	 * Resolves the {@code addReview} mutation.
	 *
	 * @param wineId the wine id
	 * @param input  the review payload
	 * @return the updated wine
	 */
	@MutationMapping
	public Wine addReview(@Argument final long wineId, @Argument final ReviewInput input)
	{
		return this.wineService.addReview(wineId, input);
	}
}
