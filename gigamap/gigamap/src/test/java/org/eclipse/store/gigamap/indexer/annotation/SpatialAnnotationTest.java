package org.eclipse.store.gigamap.indexer.annotation;

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

import org.eclipse.store.gigamap.annotations.SpatialIndex;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.gigamap.types.SpatialIndexer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class SpatialAnnotationTest
{
	@SpatialIndex(latitude = "lat", longitude = "lon")
	static class City
	{
		String name;
		double lat;
		double lon;

		City(final String name, final double lat, final double lon)
		{
			this.name = name;
			this.lat  = lat;
			this.lon  = lon;
		}
	}

	private static GigaMap<City> newMap()
	{
		final GigaMap<City> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(City.class).generateIndices(map);
		map.add(new City("Berlin", 52.520, 13.405));
		map.add(new City("Paris", 48.857, 2.352));
		map.add(new City("New York", 40.713, -74.006));
		return map;
	}

	@SuppressWarnings("unchecked")
	private static SpatialIndexer<City> spatial(final GigaMap<City> map)
	{
		final SpatialIndexer<City> indexer = map.index().bitmap().getIndexer(SpatialIndexer.class, "spatial");
		assertInstanceOf(SpatialIndexer.class, indexer);
		return indexer;
	}

	@Test
	void nearReturnsOnlyCitiesWithinRadius()
	{
		final GigaMap<City>      map     = newMap();
		final SpatialIndexer<City> spatial = spatial(map);

		final List<City> near = map.query(spatial.near(52.520, 13.405, 100)).toList();
		assertEquals(1, near.size());
		assertEquals("Berlin", near.get(0).name);

		final List<City> wider = map.query(spatial.near(52.520, 13.405, 2000)).toList();
		assertEquals(2, wider.size());
	}

	@Test
	void withinBoxReturnsCitiesInsideBoundingBox()
	{
		final GigaMap<City>      map     = newMap();
		final SpatialIndexer<City> spatial = spatial(map);

		final List<City> europe = map.query(spatial.withinBox(45.0, 55.0, 0.0, 15.0)).toList();
		assertEquals(2, europe.size());
	}
}
