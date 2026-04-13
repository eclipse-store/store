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

import java.util.Comparator;
import java.util.List;

/**
 * Application service exposing all winery-related operations against the wineries
 * {@link GigaMap} stored in the {@link DataRoot}.
 * <p>
 * Most of the interesting methods exercise the spatial index ({@link WineryIndices#LOCATION})
 * registered on the wineries collection — proximity queries
 * ({@link #nearby nearby}, {@link #nearbyWinery nearbyWinery}, {@link #kNearest kNearest}),
 * bounding-box queries ({@link #withinBox withinBox}), hemisphere filters
 * ({@link #hemisphere hemisphere}) and Haversine distance ({@link #distance distance}). Country
 * and region filters delegate to the corresponding bitmap indices.
 */
@Service
@Mutex("wineryStore")
public class WineryService
{
	private final GigaMap<Winery>      wineryGigaMap;
	private final WineryLocationIndex locationIndex = WineryIndices.LOCATION;

	/**
	 * @param dataRoot the persistent root from which the wineries GigaMap is taken
	 */
	public WineryService(final DataRoot dataRoot)
	{
		this.wineryGigaMap = dataRoot.getWineries();
	}

	/**
	 * Returns a name-sorted page of wineries.
	 *
	 * @param page the zero-based page number
	 * @param size the page size
	 * @return a page wrapping the wineries in the requested slice and the total winery count
	 */
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

	/**
	 * Looks up a single winery by its GigaMap entity id.
	 *
	 * @param id the GigaMap entity id
	 * @return the winery, or {@code null} if no winery with that id exists
	 */
	@Read
	public Winery findById(final long id)
	{
		return this.wineryGigaMap.get(id);
	}

	/**
	 * Creates and persists a new winery.
	 *
	 * @param input the winery to create
	 * @return the created winery
	 */
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

	/**
	 * Partially updates a winery. Only fields whose value in {@code input} is non-null (or, for
	 * primitives, non-zero) are applied. The mutation runs through {@link GigaMap#update} so the
	 * spatial and bitmap indices stay in sync.
	 *
	 * @param id    the GigaMap entity id of the winery to update
	 * @param input the (partial) new field values
	 * @return the updated winery, or {@code null} if no winery with that id exists
	 */
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

	/**
	 * Deletes a winery from the GigaMap.
	 *
	 * @param id the GigaMap entity id of the winery to delete
	 * @return {@code true} if a winery was deleted, {@code false} if no winery had this id
	 */
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

	/**
	 * Returns all wineries within {@code radiusKm} of the given point, sorted ascending by
	 * distance. The candidate set is cut down by the spatial index ({@code locationIndex.near})
	 * and refined by an exact Haversine check.
	 *
	 * @param lat      the query latitude in decimal degrees
	 * @param lon      the query longitude in decimal degrees
	 * @param radiusKm the search radius in kilometres
	 * @return matching wineries with their distance from the query point (rounded to 0.1 km)
	 */
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

	/**
	 * Returns wineries near another winery, excluding the anchor winery itself.
	 *
	 * @param id       the GigaMap entity id of the anchor winery
	 * @param radiusKm the search radius in kilometres
	 * @return matching wineries with their distance from the anchor; an empty list if {@code id}
	 *         is unknown
	 */
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

	/**
	 * Returns all wineries whose location falls within the axis-aligned latitude/longitude
	 * bounding box, sorted alphabetically.
	 *
	 * @param minLat the minimum latitude (inclusive)
	 * @param maxLat the maximum latitude (inclusive)
	 * @param minLon the minimum longitude (inclusive)
	 * @param maxLon the maximum longitude (inclusive)
	 * @return name-sorted matching wineries
	 */
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

	/**
	 * Returns all wineries on the given hemisphere.
	 *
	 * @param name one of {@code "north"}, {@code "south"}, {@code "east"}, {@code "west"}
	 *             (case-insensitive); any other value yields an empty list
	 * @return name-sorted matching wineries
	 */
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

	/**
	 * Returns the {@code k} wineries nearest to the given anchor winery, sorted ascending by
	 * Haversine distance. The anchor itself is excluded.
	 *
	 * @param id the GigaMap entity id of the anchor winery
	 * @param k  the number of neighbours to return
	 * @return the {@code k} nearest wineries with their distances; an empty list if {@code id}
	 *         is unknown
	 */
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

	/**
	 * Computes the great-circle distance between two wineries (Haversine formula, rounded to
	 * 0.1 km).
	 *
	 * @param fromId the GigaMap entity id of the first winery
	 * @param toId   the GigaMap entity id of the second winery
	 * @return the distance in kilometres, or {@code null} if either winery is unknown
	 */
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

	/**
	 * Returns the distinct set of countries that appear in the wineries collection, taken
	 * directly from the bitmap index keys (no full scan).
	 *
	 * @return the distinct country list
	 */
	@Read
	public List<String> countries()
	{
		return WineryIndices.COUNTRY.resolveKeys(this.wineryGigaMap);
	}

	/**
	 * Returns all wineries from a given country, served by the {@link WineryIndices#COUNTRY}
	 * bitmap index.
	 *
	 * @param country the country to filter by
	 * @return name-sorted matching wineries
	 */
	@Read
	public List<Winery> byCountry(final String country)
	{
		return this.wineryGigaMap.query(WineryIndices.COUNTRY.is(country))
			.stream()
			.sorted(Comparator.comparing(Winery::getName))
			.toList();
	}

	/**
	 * Returns all wineries from a given region, served by the {@link WineryIndices#REGION}
	 * bitmap index.
	 *
	 * @param region the region to filter by
	 * @return name-sorted matching wineries
	 */
	@Read
	public List<Winery> byRegion(final String region)
	{
		return this.wineryGigaMap.query(WineryIndices.REGION.is(region))
			.stream()
			.sorted(Comparator.comparing(Winery::getName))
			.toList();
	}
}
