package org.eclipse.store.gigamap.codegen.test;

/*-
 * #%L
 * EclipseStore GigaMap Codegen
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

import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for the PR review findings: an index name that is not a valid Java identifier must
 * still produce a compilable, sanitized constant, and index-annotated members inherited from a
 * different-package superclass must be read through accessible members.
 */
public class ReviewFixesTest
{
	@Test
	void spatialIndexNameIsSanitizedToValidIdentifier()
	{
		final GigaMap<GeoEntity> map = GigaMap.New();
		GeoEntity_.registerIndices(map);
		map.add(new GeoEntity(52.520, 13.405));
		map.add(new GeoEntity(48.137, 11.575));

		// "geo-index" -> GeoEntity_.geo_index
		assertEquals(1, map.query(GeoEntity_.geo_index.near(52.5, 13.4, 50)).toList().size());
	}

	@Test
	void inheritedMembersFromAnotherPackageAreReadThroughAccessibleMembers()
	{
		final GigaMap<InheritingEntity> map = GigaMap.New();
		InheritingEntity_.registerIndices(map);
		map.add(new InheritingEntity("eu", "alpha", "first"));
		map.add(new InheritingEntity("us", "beta", "second"));

		assertEquals(1, map.query(InheritingEntity_.region.is("eu")).toList().size()); // public field, direct
		assertEquals(1, map.query(InheritingEntity_.tag.is("beta")).toList().size());  // private field, via getter
		assertEquals(1, map.query(InheritingEntity_.name.is("first")).toList().size()); // own field
	}
}
