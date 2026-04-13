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

import org.eclipse.store.demo.vinoteca.dto.NearbyWineryResult;
import org.eclipse.store.demo.vinoteca.dto.PageResult;
import org.eclipse.store.demo.vinoteca.dto.WineryInput;
import org.eclipse.store.demo.vinoteca.model.Winery;
import org.eclipse.store.demo.vinoteca.service.WineryService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * GraphQL controller backing all winery-related queries and mutations declared in
 * {@code schema.graphqls}.
 * <p>
 * The {@code wineries} resolver returns a plain {@link Map} shaped like the GraphQL
 * {@code WineryPage} type; all other resolvers return domain objects directly.
 */
@Controller
public class WineryGraphqlController
{
	private final WineryService wineryService;

	/**
	 * @param wineryService the underlying winery application service
	 */
	public WineryGraphqlController(final WineryService wineryService)
	{
		this.wineryService = wineryService;
	}

	/**
	 * Resolves the {@code wineries} query — a paged listing of wineries.
	 *
	 * @param page zero-based page number
	 * @param size page size
	 * @return a map shaped like the GraphQL {@code WineryPage} type
	 */
	@QueryMapping
	public Map<String, Object> wineries(@Argument final int page, @Argument final int size)
	{
		final PageResult<Winery> result = this.wineryService.list(page, size);
		return Map.of(
			"content", result.content(),
			"total",   result.total(),
			"page",    result.page(),
			"size",    result.size()
		);
	}

	/**
	 * Resolves the {@code winery(id)} query.
	 *
	 * @param id the winery id
	 * @return the winery, or {@code null} if no winery with that id exists
	 */
	@QueryMapping
	public Winery winery(@Argument final long id)
	{
		return this.wineryService.findById(id);
	}

	/**
	 * Resolves the {@code nearbyWineries(lat, lon, radiusKm)} query (spatial-index lookup).
	 *
	 * @param lat      query latitude in decimal degrees
	 * @param lon      query longitude in decimal degrees
	 * @param radiusKm search radius in kilometres
	 * @return matching wineries with their distance from the query point
	 */
	@QueryMapping
	public List<NearbyWineryResult> nearbyWineries(
		@Argument final double lat,
		@Argument final double lon,
		@Argument final double radiusKm
	)
	{
		return this.wineryService.nearby(lat, lon, radiusKm);
	}

	/**
	 * Resolves the {@code wineriesByCountry(country)} query (bitmap-index lookup).
	 *
	 * @param country the country
	 * @return matching wineries
	 */
	@QueryMapping
	public List<Winery> wineriesByCountry(@Argument final String country)
	{
		return this.wineryService.byCountry(country);
	}

	/**
	 * Resolves the {@code wineriesByRegion(region)} query (bitmap-index lookup).
	 *
	 * @param region the region
	 * @return matching wineries
	 */
	@QueryMapping
	public List<Winery> wineriesByRegion(@Argument final String region)
	{
		return this.wineryService.byRegion(region);
	}

	/**
	 * Resolves the {@code createWinery} mutation.
	 *
	 * @param input the winery to create
	 * @return the created winery
	 */
	@MutationMapping
	public Winery createWinery(@Argument final WineryInput input)
	{
		return this.wineryService.create(input);
	}

	/**
	 * Resolves the {@code updateWinery} mutation (partial update).
	 *
	 * @param id    the winery id
	 * @param input the (partial) new field values
	 * @return the updated winery, or {@code null} if no winery with that id exists
	 */
	@MutationMapping
	public Winery updateWinery(@Argument final long id, @Argument final WineryInput input)
	{
		return this.wineryService.update(id, input);
	}
}
