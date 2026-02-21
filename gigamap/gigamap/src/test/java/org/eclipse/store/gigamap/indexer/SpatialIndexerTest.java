package org.eclipse.store.gigamap.indexer;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.SpatialIndexer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class SpatialIndexerTest
{
	@TempDir
	Path tempDir;

	private final LocationIndex locationIndex = new LocationIndex();

	@Test
	void at_exactMatch()
	{
		final GigaMap<Location> map = prepareGigaMap();

		assertEquals(1, map.query(locationIndex.at(40.7128, -74.0060)).count());
		assertEquals(1, map.query(locationIndex.at(51.5074, -0.1278)).count());
		assertEquals(1, map.query(locationIndex.at(35.6762, 139.6503)).count());
		assertEquals(1, map.query(locationIndex.at(-33.8688, 151.2093)).count());
		assertEquals(1, map.query(locationIndex.at(-33.9249, 18.4241)).count());
		assertEquals(1, map.query(locationIndex.at(-17.7134, 178.065)).count());
		assertEquals(0, map.query(locationIndex.at(0.0, 0.0)).count());
	}

	@Test
	void isNull()
	{
		final GigaMap<Location> map = prepareGigaMap();

		assertEquals(1, map.query(locationIndex.isNull()).count());
		map.query(locationIndex.isNull()).forEach(
			location -> assertEquals("Nowhere", location.name)
		);
	}

	@Test
	void latitudeBetween()
	{
		final GigaMap<Location> map = prepareGigaMap();

		// Northern hemisphere: New York, London, Tokyo
		final long northern = map.query(locationIndex.latitudeBetween(0.0, 90.0)).count();
		assertEquals(3, northern);

		// Southern hemisphere: Sydney, Cape Town, Fiji
		final long southern = map.query(locationIndex.latitudeBetween(-90.0, 0.0)).count();
		assertEquals(3, southern);
	}

	@Test
	void longitudeBetween()
	{
		final GigaMap<Location> map = prepareGigaMap();

		// Eastern hemisphere (positive longitude): Tokyo, Sydney, Cape Town, Fiji
		final long eastern = map.query(locationIndex.longitudeBetween(0.0, 180.0)).count();
		assertEquals(4, eastern);

		// Western hemisphere (negative longitude): New York, London (barely west)
		final long western = map.query(locationIndex.longitudeBetween(-180.0, 0.0)).count();
		assertEquals(2, western);
	}

	@Test
	void latitudeAbove()
	{
		final GigaMap<Location> map = prepareGigaMap();

		// Above 40 degrees: New York (40.7128), London (51.5074)
		final long count = map.query(locationIndex.latitudeAbove(40.0)).count();
		assertEquals(2, count);
	}

	@Test
	void latitudeBelow()
	{
		final GigaMap<Location> map = prepareGigaMap();

		// Below -30 degrees: Sydney (-33.8688), Cape Town (-33.9249)
		final long count = map.query(locationIndex.latitudeBelow(-30.0)).count();
		assertEquals(2, count);
	}

	@Test
	void longitudeAbove()
	{
		final GigaMap<Location> map = prepareGigaMap();

		// Above 100 degrees: Tokyo (139.6503), Sydney (151.2093), Fiji (178.065)
		final long count = map.query(locationIndex.longitudeAbove(100.0)).count();
		assertEquals(3, count);
	}

	@Test
	void longitudeBelow()
	{
		final GigaMap<Location> map = prepareGigaMap();

		// Below -50 degrees: New York (-74.006)
		final long count = map.query(locationIndex.longitudeBelow(-50.0)).count();
		assertEquals(1, count);
	}

	@Test
	void withinBox()
	{
		final GigaMap<Location> map = prepareGigaMap();

		// Bounding box around Europe (roughly): only London
		final long count = map.query(locationIndex.withinBox(35.0, 60.0, -10.0, 30.0)).count();
		assertEquals(1, count);
		map.query(locationIndex.withinBox(35.0, 60.0, -10.0, 30.0)).forEach(
			location -> assertEquals("London", location.name)
		);
	}

	@Test
	void near()
	{
		final GigaMap<Location> map = prepareGigaMap();

		// 100km around New York: only NYC
		final long count = map.query(locationIndex.near(40.7128, -74.0060, 100.0)).count();
		assertEquals(1, count);

		// Very large radius (20000 km) should catch everything except null
		final long all = map.query(locationIndex.near(0.0, 0.0, 20000.0)).count();
		assertTrue(all >= 5);
	}

	@Test
	void negativeCoordinates()
	{
		final GigaMap<Location> map = prepareGigaMap();

		// Both Sydney and Cape Town have negative latitudes in a similar range
		final long count = map.query(locationIndex.latitudeBetween(-34.0, -33.5)).count();
		assertEquals(2, count);

		// Only Cape Town has longitude around 18
		final long capeTown = map.query(locationIndex.withinBox(-35.0, -33.0, 17.0, 20.0)).count();
		assertEquals(1, capeTown);
	}

	@Test
	void boundaryValues()
	{
		final GigaMap<Location> map = GigaMap.New();
		final BitmapIndices<Location> bitmap = map.index().bitmap();
		bitmap.add(locationIndex);

		map.addAll(
			new Location("NorthPole", 90.0, 0.0),
			new Location("SouthPole", -90.0, 0.0),
			new Location("DatelineEast", 0.0, 180.0),
			new Location("DatelineWest", 0.0, -180.0)
		);

		assertEquals(1, map.query(locationIndex.at(90.0, 0.0)).count());
		assertEquals(1, map.query(locationIndex.at(-90.0, 0.0)).count());
		assertEquals(1, map.query(locationIndex.at(0.0, 180.0)).count());
		assertEquals(1, map.query(locationIndex.at(0.0, -180.0)).count());

		// Full range should include all
		assertEquals(4, map.query(locationIndex.latitudeBetween(-90.0, 90.0)).count());
		assertEquals(4, map.query(locationIndex.longitudeBetween(-180.0, 180.0)).count());
	}

	@Test
	void withinRadius_postFilter()
	{
		final GigaMap<Location> map = GigaMap.New();
		final BitmapIndices<Location> bitmap = map.index().bitmap();
		bitmap.add(locationIndex);

		// Center: 40.0, -74.0, radius: 150km
		// Close: 40.5, -74.0 (~55km north, inside circle)
		// Corner: 41.0, -72.5 (~169km away, inside bounding box but outside circle)
		map.addAll(
			new Location("Close", 40.5, -74.0),
			new Location("Corner", 41.0, -72.5)
		);

		// Bounding box for 150km should include both
		final long nearCount = map.query(locationIndex.near(40.0, -74.0, 150.0)).count();
		assertEquals(2, nearCount);

		// Post-filter with withinRadius should exclude "Corner" (outside the circle)
		final List<Location> filtered = map.query(locationIndex.near(40.0, -74.0, 150.0))
			.stream()
			.filter(locationIndex.withinRadius(40.0, -74.0, 150.0))
			.collect(Collectors.toList());
		assertEquals(1, filtered.size());
		assertEquals("Close", filtered.get(0).name);
	}

	@Test
	void haversineDistance_knownValues()
	{
		// New York to London: ~5570 km
		final double nyToLondon = SpatialIndexer.haversineDistance(40.7128, -74.0060, 51.5074, -0.1278);
		assertEquals(5570.0, nyToLondon, 50.0);

		// Same point: 0 km
		final double zero = SpatialIndexer.haversineDistance(40.7128, -74.0060, 40.7128, -74.0060);
		assertEquals(0.0, zero, 0.001);

		// North pole to south pole: ~20015 km (half circumference)
		final double poleToPool = SpatialIndexer.haversineDistance(90.0, 0.0, -90.0, 0.0);
		assertEquals(20015.0, poleToPool, 100.0);
	}

	@Test
	void antimeridian_withinBox()
	{
		final GigaMap<Location> map = prepareGigaMap();

		// Wrapping box: from 170 to -170 longitude (20-degree strip crossing dateline)
		// Fiji (178.065) should be in this box
		final long count = map.query(locationIndex.withinBox(-20.0, -15.0, 170.0, -170.0)).count();
		assertEquals(1, count);
		map.query(locationIndex.withinBox(-20.0, -15.0, 170.0, -170.0)).forEach(
			location -> assertEquals("Fiji", location.name)
		);
	}

	@Test
	void antimeridian_near()
	{
		final GigaMap<Location> map = prepareGigaMap();

		// Near query centered at 179 degrees longitude, should cross antimeridian
		// Fiji is at 178.065, should be within ~200km
		final long count = map.query(locationIndex.near(-17.7134, 179.0, 200.0)).count();
		assertTrue(count >= 1, "Fiji should be found near 179° longitude");
	}

	@Test
	void persistence_roundTrip_rangeQueries()
	{
		final GigaMap<Location> map = prepareGigaMap();

		try(EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir))
		{
			assertEquals(3, map.query(locationIndex.latitudeBetween(0.0, 90.0)).count());
			assertEquals(1, map.query(locationIndex.withinBox(35.0, 60.0, -10.0, 30.0)).count());
		}

		try(EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Location> loaded = (GigaMap<Location>)manager.root();

			assertEquals(3, loaded.query(locationIndex.latitudeBetween(0.0, 90.0)).count());
			assertEquals(1, loaded.query(locationIndex.withinBox(35.0, 60.0, -10.0, 30.0)).count());
		}
	}

	@Test
	void persistence_roundTrip_exactMatch()
	{
		final GigaMap<Location> map = prepareGigaMap();

		try(EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir))
		{
			assertEquals(1, map.query(locationIndex.at(40.7128, -74.0060)).count());
		}

		try(EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Location> loaded = (GigaMap<Location>)manager.root();

			assertEquals(1, loaded.query(locationIndex.at(40.7128, -74.0060)).count());
		}
	}

	@Test
	void nullArguments()
	{
		final GigaMap<Location> map = GigaMap.New();
		final BitmapIndices<Location> bitmap = map.index().bitmap();
		bitmap.add(locationIndex);

		// Partially null coordinates should throw
		assertThrows(IllegalArgumentException.class, () ->
			map.add(new Location("BadLatOnly", 40.0, null))
		);
		assertThrows(IllegalArgumentException.class, () ->
			map.add(new Location("BadLonOnly", null, -74.0))
		);
	}


	// ---- test infrastructure ----

	private GigaMap<Location> prepareGigaMap()
	{
		final GigaMap<Location> map = GigaMap.New();
		final BitmapIndices<Location> bitmap = map.index().bitmap();
		bitmap.add(locationIndex);

		map.addAll(
			new Location("New York",  40.7128,  -74.0060),
			new Location("London",    51.5074,   -0.1278),
			new Location("Tokyo",     35.6762,  139.6503),
			new Location("Sydney",   -33.8688,  151.2093),
			new Location("Cape Town",-33.9249,   18.4241),
			new Location("Fiji",     -17.7134,  178.065 ),
			new Location("Nowhere",   null,      null   )
		);

		return map;
	}


	private static class LocationIndex extends SpatialIndexer.Abstract<Location>
	{
		@Override
		protected Double getLatitude(final Location entity)
		{
			return entity.latitude;
		}

		@Override
		protected Double getLongitude(final Location entity)
		{
			return entity.longitude;
		}
	}


	private static class Location
	{
		final String name;
		final Double latitude;
		final Double longitude;

		Location(final String name, final Double latitude, final Double longitude)
		{
			this.name      = name;
			this.latitude  = latitude;
			this.longitude = longitude;
		}
	}
}
