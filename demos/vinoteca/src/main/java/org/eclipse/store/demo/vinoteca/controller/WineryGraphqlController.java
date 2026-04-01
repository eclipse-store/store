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

@Controller
public class WineryGraphqlController
{
	private final WineryService wineryService;

	public WineryGraphqlController(final WineryService wineryService)
	{
		this.wineryService = wineryService;
	}

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

	@QueryMapping
	public Winery winery(@Argument final long id)
	{
		return this.wineryService.findById(id);
	}

	@QueryMapping
	public List<NearbyWineryResult> nearbyWineries(
		@Argument final double lat,
		@Argument final double lon,
		@Argument final double radiusKm
	)
	{
		return this.wineryService.nearby(lat, lon, radiusKm);
	}

	@QueryMapping
	public List<Winery> wineriesByCountry(@Argument final String country)
	{
		return this.wineryService.byCountry(country);
	}

	@QueryMapping
	public List<Winery> wineriesByRegion(@Argument final String region)
	{
		return this.wineryService.byRegion(region);
	}

	@MutationMapping
	public Winery createWinery(@Argument final WineryInput input)
	{
		return this.wineryService.create(input);
	}

	@MutationMapping
	public Winery updateWinery(@Argument final long id, @Argument final WineryInput input)
	{
		return this.wineryService.update(id, input);
	}
}
