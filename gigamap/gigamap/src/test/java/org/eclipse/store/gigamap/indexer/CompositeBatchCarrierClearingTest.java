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


import org.eclipse.store.gigamap.types.BinaryCompositeIndexer;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.HashingCompositeIndexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test for GitHub issue: the composite index key carrier is not cleared BETWEEN the elements
 * of an {@code addAll} batch.
 * <p>
 * Root cause: {@code AbstractCompositeBitmapIndex.internalAddAll} cleared the shared carrier
 * only <strong>after</strong> the whole batch, while the inherited loop invoked
 * {@code indexer.index(entity, carrier())} per element on the SAME array. A composite indexer
 * that writes a position only when its component is present - the "unwritten position = empty"
 * pattern that the per-single-add {@code clearCarrier()} calls exist to support - therefore
 * inherited the carrier positions of its batch predecessor: the same entities got different
 * index keys via {@code add()} vs {@code addAll()}.
 * <p>
 * Fix: the carrier is cleared before every {@code indexer.index(entity, carrier)} invocation,
 * making batch semantics identical to per-element single adds by construction.
 */
public class CompositeBatchCarrierClearingTest
{
	static final class Rec
	{
		final long a;
		final long b; // 0 means "absent" (optional component)

		Rec(final long a, final long b)
		{
			this.a = a;
			this.b = b;
		}
	}

	/**
	 * Optional-component binary composite: writes carrier[1] only when the component is present -
	 * the pattern the framework's per-single-add clearCarrier() calls exist to support.
	 */
	static final class BinaryOptIdx extends BinaryCompositeIndexer.Abstract<Rec>
	{
		@Override
		public String name()
		{
			return "binaryOpt";
		}

		@Override
		public long[] index(final Rec entity, final long[] carrier)
		{
			final long[] result = carrier != null && carrier.length >= 2 ? carrier : new long[2];
			result[0] = entity.a;
			if(entity.b != 0L)
			{
				result[1] = entity.b;
			}
			return result;
		}
	}

	/**
	 * Same optional-component pattern for the hashing composite family ({@code Object[]} carrier,
	 * empty position = null).
	 */
	static final class HashingOptIdx extends HashingCompositeIndexer.Abstract<Rec>
	{
		@Override
		public String name()
		{
			return "hashingOpt";
		}

		@Override
		public Object[] index(final Rec entity, final Object[] carrier)
		{
			final Object[] result = carrier != null && carrier.length >= 2 ? carrier : new Object[2];
			result[0] = entity.a;
			if(entity.b != 0L)
			{
				result[1] = entity.b;
			}
			return result;
		}
	}

	@Test
	@Timeout(60)
	void binary_batchAddMustKeyElementsExactlyLikeSingleAdds()
	{
		final BinaryOptIdx idx = new BinaryOptIdx();

		// reference: single adds - correct keys
		final GigaMap<Rec> single = GigaMap.New();
		single.index().bitmap().add(idx);
		single.add(new Rec(1, 7));
		single.add(new Rec(2, 0));
		assertEquals(1, single.query(idx.is(new long[]{2, 0})).count(), "single add: true key");
		assertEquals(0, single.query(idx.is(new long[]{2, 7})).count(), "single add: no alias");

		// the SAME entities as a batch
		final GigaMap<Rec> batch = GigaMap.New();
		batch.index().bitmap().add(idx);
		batch.addAll(List.of(new Rec(1, 7), new Rec(2, 0)));

		assertEquals(1, batch.query(idx.is(new long[]{2, 0})).count(),
			"CARRIER LEAK: the optional-absent batch element must be keyed [2,<empty>] exactly like "
			+ "a single add - the shared carrier must be cleared per batch element, otherwise "
			+ "the element inherits its predecessor's component");
		assertEquals(0, batch.query(idx.is(new long[]{2, 7})).count(),
			"CARRIER LEAK: entity (2,absent) must not be findable under the predecessor's b=7");
	}

	@Test
	@Timeout(60)
	void binary_varargsBatchAddMustKeyElementsExactlyLikeSingleAdds()
	{
		final BinaryOptIdx idx = new BinaryOptIdx();

		final GigaMap<Rec> batch = GigaMap.New();
		batch.index().bitmap().add(idx);
		batch.addAll(new Rec(1, 7), new Rec(2, 0));

		assertEquals(1, batch.query(idx.is(new long[]{2, 0})).count(),
			"CARRIER LEAK (varargs addAll): the optional-absent batch element must be keyed [2,<empty>]");
		assertEquals(0, batch.query(idx.is(new long[]{2, 7})).count(),
			"CARRIER LEAK (varargs addAll): entity (2,absent) must not be findable under the predecessor's b=7");
	}

	@Test
	@Timeout(60)
	void hashing_batchAddMustKeyElementsExactlyLikeSingleAdds()
	{
		// note: for the hashing family a null sample position is a WILDCARD (ObjectSampleBased),
		// so the discriminating assertion is the alias check on the predecessor's component value.
		final HashingOptIdx idx = new HashingOptIdx();

		// reference: single adds - correct keys
		final GigaMap<Rec> single = GigaMap.New();
		single.index().bitmap().add(idx);
		single.add(new Rec(1, 7));
		single.add(new Rec(2, 0));
		assertEquals(1, single.query(idx.is(new Object[]{2L, null})).count(), "single add: found by first component");
		assertEquals(0, single.query(idx.is(new Object[]{2L, 7L})).count(), "single add: no alias");

		// the SAME entities as a batch
		final GigaMap<Rec> batch = GigaMap.New();
		batch.index().bitmap().add(idx);
		batch.addAll(List.of(new Rec(1, 7), new Rec(2, 0)));

		assertEquals(1, batch.query(idx.is(new Object[]{2L, null})).count(),
			"batch element must be findable by its first component");
		assertEquals(0, batch.query(idx.is(new Object[]{2L, 7L})).count(),
			"CARRIER LEAK: entity (2,absent) must not be findable under the predecessor's b=7 - "
			+ "the shared carrier must be cleared per batch element, otherwise the element "
			+ "inherits its predecessor's component");
	}
}
