package org.eclipse.store.gigamap.indexer;

/*-
 * #%L
 * EclipseStore GigaMap
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

import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.Condition;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.gigamap.types.SpatialIndexer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SpatialIndexerTest
{
	@TempDir
	Path tempDir;

	private final LocationIndex locationIndex = new LocationIndex();

	@Test
	void at_exactMatch()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		assertEquals(1, map.query(this.locationIndex.at(40.7128, -74.0060)).count());
		assertEquals(1, map.query(this.locationIndex.at(51.5074, -0.1278)).count());
		assertEquals(1, map.query(this.locationIndex.at(35.6762, 139.6503)).count());
		assertEquals(1, map.query(this.locationIndex.at(-33.8688, 151.2093)).count());
		assertEquals(1, map.query(this.locationIndex.at(-33.9249, 18.4241)).count());
		assertEquals(1, map.query(this.locationIndex.at(-17.7134, 178.065)).count());
		assertEquals(0, map.query(this.locationIndex.at(0.0, 0.0)).count());
	}

	@Test
	void isNull()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		assertEquals(1, map.query(this.locationIndex.isNull()).count());
		map.query(this.locationIndex.isNull()).forEach(
			location -> assertEquals("Nowhere", location.name)
		);
	}

	@Test
	void latitudeBetween()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		// Northern hemisphere: New York, London, Tokyo
		final long northern = map.query(this.locationIndex.latitudeBetween(0.0, 90.0)).count();
		assertEquals(3, northern);

		// Southern hemisphere: Sydney, Cape Town, Fiji
		final long southern = map.query(this.locationIndex.latitudeBetween(-90.0, 0.0)).count();
		assertEquals(3, southern);
	}

	@Test
	void longitudeBetween()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		// Eastern hemisphere (positive longitude): Tokyo, Sydney, Cape Town, Fiji
		final long eastern = map.query(this.locationIndex.longitudeBetween(0.0, 180.0)).count();
		assertEquals(4, eastern);

		// Western hemisphere (negative longitude): New York, London (barely west)
		final long western = map.query(this.locationIndex.longitudeBetween(-180.0, 0.0)).count();
		assertEquals(2, western);
	}

	@Test
	void latitudeAbove()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		// Above 40 degrees: New York (40.7128), London (51.5074)
		final long count = map.query(this.locationIndex.latitudeAbove(40.0)).count();
		assertEquals(2, count);
	}

	@Test
	void latitudeBelow()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		// Below -30 degrees: Sydney (-33.8688), Cape Town (-33.9249)
		final long count = map.query(this.locationIndex.latitudeBelow(-30.0)).count();
		assertEquals(2, count);
	}

	@Test
	void longitudeAbove()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		// Above 100 degrees: Tokyo (139.6503), Sydney (151.2093), Fiji (178.065)
		final long count = map.query(this.locationIndex.longitudeAbove(100.0)).count();
		assertEquals(3, count);
	}

	@Test
	void longitudeBelow()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		// Below -50 degrees: New York (-74.006)
		final long count = map.query(this.locationIndex.longitudeBelow(-50.0)).count();
		assertEquals(1, count);
	}

	@Test
	void withinBox()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		// Bounding box around Europe (roughly): only London
		final long count = map.query(this.locationIndex.withinBox(35.0, 60.0, -10.0, 30.0)).count();
		assertEquals(1, count);
		map.query(this.locationIndex.withinBox(35.0, 60.0, -10.0, 30.0)).forEach(
			location -> assertEquals("London", location.name)
		);
	}

	@Test
	void near()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		// 100km around New York: only NYC
		final long count = map.query(this.locationIndex.near(40.7128, -74.0060, 100.0)).count();
		assertEquals(1, count);

		// Very large radius (20000 km) should catch everything except null
		final long all = map.query(this.locationIndex.near(0.0, 0.0, 20000.0)).count();
		assertTrue(all >= 5);
	}

	@Test
	void negativeCoordinates()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		// Both Sydney and Cape Town have negative latitudes in a similar range
		final long count = map.query(this.locationIndex.latitudeBetween(-34.0, -33.5)).count();
		assertEquals(2, count);

		// Only Cape Town has longitude around 18
		final long capeTown = map.query(this.locationIndex.withinBox(-35.0, -33.0, 17.0, 20.0)).count();
		assertEquals(1, capeTown);
	}

	@Test
	void boundaryValues()
	{
		final GigaMap<Location> map = GigaMap.New();
		final BitmapIndices<Location> bitmap = map.index().bitmap();
		bitmap.add(this.locationIndex);

		map.addAll(
			new Location("NorthPole", 90.0, 0.0),
			new Location("SouthPole", -90.0, 0.0),
			new Location("DatelineEast", 0.0, 180.0),
			new Location("DatelineWest", 0.0, -180.0)
		);

		assertEquals(1, map.query(this.locationIndex.at(90.0, 0.0)).count());
		assertEquals(1, map.query(this.locationIndex.at(-90.0, 0.0)).count());
		assertEquals(1, map.query(this.locationIndex.at(0.0, 180.0)).count());
		assertEquals(1, map.query(this.locationIndex.at(0.0, -180.0)).count());

		// Full range should include all
		assertEquals(4, map.query(this.locationIndex.latitudeBetween(-90.0, 90.0)).count());
		assertEquals(4, map.query(this.locationIndex.longitudeBetween(-180.0, 180.0)).count());
	}

	@Test
	void withinRadius_postFilter()
	{
		final GigaMap<Location> map = GigaMap.New();
		final BitmapIndices<Location> bitmap = map.index().bitmap();
		bitmap.add(this.locationIndex);

		// Center: 40.0, -74.0, radius: 150km
		// Close: 40.5, -74.0 (~55km north, inside circle)
		// Corner: 41.0, -72.5 (~169km away, inside bounding box but outside circle)
		map.addAll(
			new Location("Close", 40.5, -74.0),
			new Location("Corner", 41.0, -72.5)
		);

		// Bounding box for 150km should include both
		final long nearCount = map.query(this.locationIndex.near(40.0, -74.0, 150.0)).count();
		assertEquals(2, nearCount);

		// Post-filter with withinRadius should exclude "Corner" (outside the circle)
		final List<Location> filtered = map.query(this.locationIndex.near(40.0, -74.0, 150.0))
			.stream()
			.filter(this.locationIndex.withinRadius(40.0, -74.0, 150.0))
			.toList();
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
		final double poleToPole = SpatialIndexer.haversineDistance(90.0, 0.0, -90.0, 0.0);
		assertEquals(20015.0, poleToPole, 100.0);
	}

	@Test
	void antimeridian_withinBox()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		// Wrapping box: from 170 to -170 longitude (20-degree strip crossing dateline)
		// Fiji (178.065) should be in this box
		final long count = map.query(this.locationIndex.withinBox(-20.0, -15.0, 170.0, -170.0)).count();
		assertEquals(1, count);
		map.query(this.locationIndex.withinBox(-20.0, -15.0, 170.0, -170.0)).forEach(
			location -> assertEquals("Fiji", location.name)
		);
	}

	@Test
	void antimeridian_near()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		// Near query centered at 179 degrees longitude, should cross antimeridian
		// Fiji is at 178.065, should be within ~200km
		final long count = map.query(this.locationIndex.near(-17.7134, 179.0, 200.0)).count();
		assertTrue(count >= 1, "Fiji should be found near 179° longitude");
	}

	@Test
	void persistence_roundTrip_rangeQueries()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir))
		{
			assertEquals(3, map.query(this.locationIndex.latitudeBetween(0.0, 90.0)).count());
			assertEquals(1, map.query(this.locationIndex.withinBox(35.0, 60.0, -10.0, 30.0)).count());
		}

		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir))
		{
			final GigaMap<Location> loaded = manager.root();

			assertEquals(3, loaded.query(this.locationIndex.latitudeBetween(0.0, 90.0)).count());
			assertEquals(1, loaded.query(this.locationIndex.withinBox(35.0, 60.0, -10.0, 30.0)).count());
		}
	}

	@Test
	void persistence_roundTrip_exactMatch()
	{
		final GigaMap<Location> map = this.prepareGigaMap();

		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir))
		{
			assertEquals(1, map.query(this.locationIndex.at(40.7128, -74.0060)).count());
		}

		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir))
		{
			final GigaMap<Location> loaded = manager.root();

			assertEquals(1, loaded.query(this.locationIndex.at(40.7128, -74.0060)).count());
		}
	}

	@Test
	void nullArguments()
	{
		final GigaMap<Location> map = GigaMap.New();
		final BitmapIndices<Location> bitmap = map.index().bitmap();
		bitmap.add(this.locationIndex);

		// Partially null coordinates should throw
		assertThrows(IllegalArgumentException.class, () ->
			map.add(new Location("BadLatOnly", 40.0, null))
		);
		assertThrows(IllegalArgumentException.class, () ->
			map.add(new Location("BadLonOnly", null, -74.0))
		);

		// Out-of-range latitude should throw (valid range: -90 to 90)
		assertThrows(IllegalArgumentException.class, () ->
			map.add(new Location("LatTooHigh", 91.0, 0.0))
		);
		assertThrows(IllegalArgumentException.class, () ->
			map.add(new Location("LatTooLow", -91.0, 0.0))
		);

		// Out-of-range longitude should throw (valid range: -180 to 180)
		assertThrows(IllegalArgumentException.class, () ->
			map.add(new Location("LonTooHigh", 0.0, 181.0))
		);
		assertThrows(IllegalArgumentException.class, () ->
			map.add(new Location("LonTooLow", 0.0, -181.0))
		);

		// NaN values should be rejected
		assertThrows(IllegalArgumentException.class, () ->
			map.add(new Location("LatNaN", Double.NaN, 0.0))
		);
		assertThrows(IllegalArgumentException.class, () ->
			map.add(new Location("LonNaN", 0.0, Double.NaN))
		);

		// Infinity values should be rejected
		assertThrows(IllegalArgumentException.class, () ->
			map.add(new Location("LatPosInf", Double.POSITIVE_INFINITY, 0.0))
		);
		assertThrows(IllegalArgumentException.class, () ->
			map.add(new Location("LatNegInf", Double.NEGATIVE_INFINITY, 0.0))
		);
		assertThrows(IllegalArgumentException.class, () ->
			map.add(new Location("LonPosInf", 0.0, Double.POSITIVE_INFINITY))
		);
		assertThrows(IllegalArgumentException.class, () ->
			map.add(new Location("LonNegInf", 0.0, Double.NEGATIVE_INFINITY))
		);
	}


	/**
	 * Regression test for issue #653 / #654: composing a {@link SpatialIndexer} range
	 * condition via {@link Condition#and(Condition)} (i) with another range condition and
	 * (ii) with a condition from a different indexer type must produce the same result as
	 * composing via {@link org.eclipse.store.gigamap.types.GigaQuery#and(Condition)}.
	 * Also covers (iii) latitudeBetween().and(longitudeBetween()) — 2D box composition where
	 * the two operands are themselves AND-of-Term, exercising And.linkCondition flattening.
	 */
	@Test
	void rangeCompositionViaConditionAnd()
	{
		final NameIndex          nameIdx = new NameIndex();
		final GigaMap<Location>  map     = GigaMap.<Location>Builder()
			.withBitmapIndex(this.locationIndex)
			.withBitmapIndex(nameIdx)
			.build();
		map.addAll(
			new Location("New York",   40.7128,  -74.0060),
			new Location("London",     51.5074,   -0.1278),
			new Location("Tokyo",      35.6762,  139.6503),
			new Location("Sydney",    -33.8688,  151.2093),
			new Location("Cape Town", -33.9249,   18.4241),
			new Location("Fiji",      -17.7134,  178.065 )
		);

		// (i) range AND range — northern hemisphere AND latitude < 45 → New York, Tokyo
		final Condition<Location> above = this.locationIndex.latitudeAbove(0.0);
		final Condition<Location> below = this.locationIndex.latitudeBelow(45.0);

		final List<Location> rangeAnd = map.query(above.and(below)).toList();
		final List<Location> rangeQ   = map.query().and(above).and(below).toList();

		assertEquals(2, rangeAnd.size(), "Condition.and() must respect both bounds");
		assertEquals(rangeQ.size(), rangeAnd.size(), "Condition.and() and GigaQuery.and() must agree");
		rangeAnd.forEach(l -> assertNotEquals("London", l.name));

		// (ii) range AND condition from a different indexer
		final Condition<Location> tokyoMatch = nameIdx.is("Tokyo");

		final List<Location> mixedAnd = map.query(above.and(tokyoMatch)).toList();
		final List<Location> mixedQ   = map.query().and(above).and(tokyoMatch).toList();

		assertEquals(1, mixedAnd.size(), "range AND non-range condition must intersect correctly");
		assertEquals("Tokyo", mixedAnd.get(0).name);
		assertEquals(mixedQ.size(), mixedAnd.size());

		// (iii) latitudeBetween AND longitudeBetween — both operands are And, not Or
		final Condition<Location> latBox = this.locationIndex.latitudeBetween(0.0, 45.0);
		final Condition<Location> lonBox = this.locationIndex.longitudeBetween(100.0, 180.0);

		final List<Location> boxAnd = map.query(latBox.and(lonBox)).toList();
		final List<Location> boxQ   = map.query().and(latBox).and(lonBox).toList();

		assertEquals(1, boxAnd.size(), "2D box composition must intersect correctly");
		assertEquals("Tokyo", boxAnd.get(0).name);
		assertEquals(boxQ.size(), boxAnd.size());
	}


	// ---- test infrastructure ----

	private GigaMap<Location> prepareGigaMap()
	{
		final GigaMap<Location> map = GigaMap.New();
		final BitmapIndices<Location> bitmap = map.index().bitmap();
		bitmap.add(this.locationIndex);

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


	private static class NameIndex extends IndexerString.Abstract<Location>
	{
		@Override
		protected String getString(final Location entity)
		{
			return entity.name;
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
