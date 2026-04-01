package org.eclipse.store.demo.vinoteca.service;

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

import java.util.Comparator;
import java.util.List;

import org.eclipse.store.demo.vinoteca.dto.NearbyWineryResult;
import org.eclipse.store.demo.vinoteca.dto.PageResult;
import org.eclipse.store.demo.vinoteca.dto.WineryInput;
import org.eclipse.store.demo.vinoteca.index.WineryIndices;
import org.eclipse.store.demo.vinoteca.index.WineryLocationIndex;
import org.eclipse.store.demo.vinoteca.model.DataRoot;
import org.eclipse.store.demo.vinoteca.model.Winery;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.SpatialIndexer;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Mutex;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Read;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Write;
import org.springframework.stereotype.Service;

@Service
@Mutex("wineryStore")
public class WineryService
{
	private final GigaMap<Winery>      wineryGigaMap;
	private final WineryLocationIndex locationIndex = WineryIndices.LOCATION;

	public WineryService(final DataRoot dataRoot)
	{
		this.wineryGigaMap = dataRoot.getWineries();
	}

	@Read
	public PageResult<Winery> list(final int page, final int size)
	{
		final long total = this.wineryGigaMap.size();
		final List<Winery> wineries = this.wineryGigaMap.query()
			.stream()
			.sorted(Comparator.comparing(Winery::getName))
			.skip((long) page * size)
			.limit(size)
			.toList();
		return new PageResult<>(wineries, total, page, size);
	}

	@Read
	public Winery findById(final long id)
	{
		return this.wineryGigaMap.get(id);
	}

	@Write
	public Winery create(final WineryInput input)
	{
		final Winery winery = new Winery(
			input.name(),
			input.region(),
			input.country(),
			input.latitude(),
			input.longitude(),
			input.description(),
			input.foundedYear()
		);
		this.wineryGigaMap.add(winery);
		this.wineryGigaMap.store();
		return winery;
	}

	@Write
	public Winery update(final long id, final WineryInput input)
	{
		final Winery winery = this.wineryGigaMap.get(id);
		if (winery == null)
		{
			return null;
		}

		// Use gigaMap.update() to ensure indices are kept in sync
		this.wineryGigaMap.update(winery, w -> {
			if (input.name() != null)        w.setName(input.name());
			if (input.region() != null)      w.setRegion(input.region());
			if (input.country() != null)     w.setCountry(input.country());
			if (input.latitude() != 0)       w.setLatitude(input.latitude());
			if (input.longitude() != 0)      w.setLongitude(input.longitude());
			if (input.description() != null) w.setDescription(input.description());
			if (input.foundedYear() > 0)     w.setFoundedYear(input.foundedYear());
		});

		this.wineryGigaMap.store();
		return winery;
	}

	@Write
	public boolean delete(final long id)
	{
		final Winery winery = this.wineryGigaMap.get(id);
		if (winery == null)
		{
			return false;
		}
		this.wineryGigaMap.removeById(id);
		this.wineryGigaMap.store();
		return true;
	}

	@Read
	public List<NearbyWineryResult> nearby(final double lat, final double lon, final double radiusKm)
	{
		return this.wineryGigaMap.query(this.locationIndex.near(lat, lon, radiusKm))
			.stream()
			.filter(this.locationIndex.withinRadius(lat, lon, radiusKm))
			.map(w -> new NearbyWineryResult(
				w,
				Math.round(SpatialIndexer.haversineDistance(lat, lon, w.getLatitude(), w.getLongitude()) * 10.0) / 10.0
			))
			.sorted(Comparator.comparingDouble(NearbyWineryResult::distanceKm))
			.toList();
	}

	@Read
	public List<NearbyWineryResult> nearbyWinery(final long id, final double radiusKm)
	{
		final Winery origin = this.wineryGigaMap.get(id);
		if (origin == null)
		{
			return List.of();
		}
		return this.nearby(origin.getLatitude(), origin.getLongitude(), radiusKm)
			.stream()
			.filter(r -> r.winery() != origin)
			.toList();
	}

	@Read
	public List<Winery> withinBox(
		final double minLat, final double maxLat,
		final double minLon, final double maxLon
	)
	{
		return this.wineryGigaMap.query(this.locationIndex.withinBox(minLat, maxLat, minLon, maxLon))
			.stream()
			.sorted(Comparator.comparing(Winery::getName))
			.toList();
	}

	@Read
	public List<Winery> hemisphere(final String name)
	{
		return switch (name.toLowerCase())
		{
			case "north" -> this.wineryGigaMap.query(this.locationIndex.latitudeAbove(0.0))
				.stream().sorted(Comparator.comparing(Winery::getName)).toList();
			case "south" -> this.wineryGigaMap.query(this.locationIndex.latitudeBelow(0.0))
				.stream().sorted(Comparator.comparing(Winery::getName)).toList();
			case "east"  -> this.wineryGigaMap.query(this.locationIndex.longitudeAbove(0.0))
				.stream().sorted(Comparator.comparing(Winery::getName)).toList();
			case "west"  -> this.wineryGigaMap.query(this.locationIndex.longitudeBelow(0.0))
				.stream().sorted(Comparator.comparing(Winery::getName)).toList();
			default -> List.of();
		};
	}

	@Read
	public List<NearbyWineryResult> kNearest(final long id, final int k)
	{
		final Winery origin = this.wineryGigaMap.get(id);
		if (origin == null)
		{
			return List.of();
		}
		return this.wineryGigaMap.query()
			.stream()
			.filter(w -> w != origin)
			.map(w -> new NearbyWineryResult(
				w,
				Math.round(SpatialIndexer.haversineDistance(
					origin.getLatitude(), origin.getLongitude(),
					w.getLatitude(), w.getLongitude()
				) * 10.0) / 10.0
			))
			.sorted(Comparator.comparingDouble(NearbyWineryResult::distanceKm))
			.limit(k)
			.toList();
	}

	@Read
	public Double distance(final long fromId, final long toId)
	{
		final Winery from = this.wineryGigaMap.get(fromId);
		final Winery to   = this.wineryGigaMap.get(toId);
		if (from == null || to == null)
		{
			return null;
		}
		return Math.round(SpatialIndexer.haversineDistance(
			from.getLatitude(), from.getLongitude(),
			to.getLatitude(), to.getLongitude()
		) * 10.0) / 10.0;
	}

	@Read
	public List<Winery> byCountry(final String country)
	{
		return this.wineryGigaMap.query(WineryIndices.COUNTRY.is(country))
			.stream()
			.sorted(Comparator.comparing(Winery::getName))
			.toList();
	}

	@Read
	public List<Winery> byRegion(final String region)
	{
		return this.wineryGigaMap.query(WineryIndices.REGION.is(region))
			.stream()
			.sorted(Comparator.comparing(Winery::getName))
			.toList();
	}
}
