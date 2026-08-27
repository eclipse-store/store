package org.eclipse.store.gigamap.indexer.binary;

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

import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BinaryIndexerString} cannot encode the byte length of a key, so trailing NUL bytes vanish
 * during packing: {@code "alpha"} and {@code "alpha"}+NUL, or NUL-only strings of different length,
 * would collide on the same index key and an exact-match query would return entities that an
 * in-memory scan rejects.
 * <p>
 * The fix rejects any key containing the NUL character ({@code U+0000}) with an
 * {@link IllegalArgumentException} - turning a silent false-positive into a loud failure, mirroring
 * how {@code BinaryIndexerLong} rejects the reserved {@code Long.MAX_VALUE}. The check sits in
 * {@code fillCarrier}, which both the add path and the query path go through, so NUL is rejected on
 * both adding an entity and forming a query condition. This test verifies that contract, and that
 * normal keys / the empty string remain unaffected.
 */
public class BinaryIndexerStringNulRejectionTest
{
	record StrBox(String name, String value) {}

	static final BinaryIndexerString<StrBox> INDEX = new BinaryIndexerString.Abstract<>()
	{
		@Override
		protected String getString(final StrBox entity)
		{
			return entity.value();
		}
	};

	/** A string of {@code n} NUL characters - char[] defaults to NUL, so no raw NUL byte in source. */
	private static String nul(final int n)
	{
		return new String(new char[n]);
	}

	/** {@code "alpha"} with a single trailing NUL, built at runtime to keep the source NUL-free. */
	private static String alphaTrailingNul()
	{
		return "alpha" + nul(1);
	}

	/** {@code "a?b"} with one embedded NUL between two non-zero bytes. */
	private static String embeddedNul()
	{
		return "a" + nul(1) + "b";
	}

	private static GigaMap<StrBox> newMap()
	{
		return GigaMap.<StrBox>Builder()
			.withBitmapIndex(INDEX)
			.build();
	}

	@Test
	void nulOnlyStringRejectedOnAdd()
	{
		final GigaMap<StrBox> map = newMap();
		assertThrows(IllegalArgumentException.class,
			() -> map.add(new StrBox("one", nul(1))),
			"a NUL-only string must be rejected on add");
	}

	@Test
	void trailingNulStringRejectedOnAdd()
	{
		final GigaMap<StrBox> map = newMap();
		assertThrows(IllegalArgumentException.class,
			() -> map.add(new StrBox("padded", alphaTrailingNul())),
			"a string with a trailing NUL must be rejected on add");
	}

	@Test
	void embeddedNulStringRejectedOnAdd()
	{
		// Embedded NUL is technically distinguishable, but option B rejects ALL NUL for a simple,
		// consistent contract: the index never accepts a key it cannot guarantee to round-trip.
		final GigaMap<StrBox> map = newMap();
		assertThrows(IllegalArgumentException.class,
			() -> map.add(new StrBox("embedded", embeddedNul())),
			"a string with an embedded NUL must be rejected on add");
	}

	@Test
	void nulStringRejectedOnQuery()
	{
		// The query path (is -> isValue -> fillCarrier) must reject NUL too, before any matching.
		assertThrows(IllegalArgumentException.class,
			() -> INDEX.is(alphaTrailingNul()),
			"a NUL key must be rejected when building a query condition");
		assertThrows(IllegalArgumentException.class,
			() -> INDEX.is(nul(2)),
			"a NUL-only key must be rejected when building a query condition");
	}

	@Test
	void normalKeysStillWork()
	{
		final GigaMap<StrBox> map = newMap();
		map.add(new StrBox("plain", "alpha"));
		map.add(new StrBox("other", "beta"));

		final List<StrBox> alpha = map.query(INDEX.is("alpha")).toList();
		assertEquals(1, alpha.size(), "exact-match query for a normal key must match exactly one entity");
		assertEquals("plain", alpha.get(0).name());
	}

	@Test
	void emptyStringStillWorks()
	{
		// Regression guard for the #688 empty-string sentinel: the empty string is NUL-free and must
		// remain indexable and queryable.
		final GigaMap<StrBox> map = newMap();
		map.add(new StrBox("empty", ""));
		map.add(new StrBox("nonEmpty", "alpha"));

		final List<StrBox> empty = map.query(INDEX.is("")).toList();
		assertEquals(1, empty.size(), "the empty string must still be queryable");
		assertEquals("empty", empty.get(0).name());
	}
}
