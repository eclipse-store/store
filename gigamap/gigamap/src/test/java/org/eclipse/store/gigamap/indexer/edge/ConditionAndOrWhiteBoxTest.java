package org.eclipse.store.gigamap.indexer.edge;

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


import org.eclipse.store.gigamap.types.Condition;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Tests for the linking behavior of {@link Condition#and(Condition)} / {@link Condition#or(Condition)}
 * on an existing {@code And}/{@code Or} chain.
 * <p>
 * Root cause of both defects covered here: {@code And/Or.linkCondition} added the incoming condition to
 * <strong>this</strong> chain's condition list and returned the receiver.
 * <ul>
 * <li>A base condition that the application holds and reuses therefore accumulated every term ever
 * chained onto it, so subsequent queries built from that base silently returned wrong results - while
 * the javadoc promises "a new condition".</li>
 * <li>Mutating the receiver also allowed a reference cycle to form: chaining the receiver onto a
 * condition that already contained it ({@code c1.and(c1.or(x))}) made evaluation recurse infinitely.
 * The {@code condition == this} guard only covered the direct case.</li>
 * </ul>
 * Fix: linking copies the chain's condition list, so the receiver is never modified and the returned
 * instance can never be contained in itself.
 */
public class ConditionAndOrWhiteBoxTest
{
	static final class Rec
	{
		final String f1;
		final String f2;
		final String f3;

		Rec(final String f1, final String f2, final String f3)
		{
			this.f1 = f1;
			this.f2 = f2;
			this.f3 = f3;
		}
	}

	static final class F1Idx extends IndexerString.Abstract<Rec>
	{
		@Override
		protected String getString(final Rec entity)
		{
			return entity.f1;
		}
	}

	static final class F2Idx extends IndexerString.Abstract<Rec>
	{
		@Override
		protected String getString(final Rec entity)
		{
			return entity.f2;
		}
	}

	static final class F3Idx extends IndexerString.Abstract<Rec>
	{
		@Override
		protected String getString(final Rec entity)
		{
			return entity.f3;
		}
	}

	private final F1Idx f1 = new F1Idx();
	private final F2Idx f2 = new F2Idx();
	private final F3Idx f3 = new F3Idx();

	/**
	 * 24 entities: all 8 combinations of the three binary-valued indexed fields, 3 entities each.
	 */
	private GigaMap<Rec> newMap()
	{
		final GigaMap<Rec> map = GigaMap.New();
		map.index().bitmap().addAll(this.f1, this.f2, this.f3);

		for(int i = 0; i < 3; i++)
		{
			for(final String a : new String[]{"a0", "a1"})
			{
				for(final String b : new String[]{"b0", "b1"})
				{
					for(final String c : new String[]{"c0", "c1"})
					{
						map.add(new Rec(a, b, c));
					}
				}
			}
		}

		return map;
	}

	@Test
	@Timeout(60)
	void reusedBaseConditionMustNotBeMutatedByDerivedQueries()
	{
		final GigaMap<Rec> map = this.newMap();

		final Condition<Rec> base = this.f1.is("a0").and(this.f2.is("b0"));

		assertEquals(6, map.query(base).count(), "base condition (f1 AND f2)");

		final Condition<Rec> derived = base.and(this.f3.is("c0"));
		assertEquals(3, map.query(derived).count(), "derived condition (f1 AND f2 AND f3)");

		assertEquals(
			6,
			map.query(base).count(),
			"deriving a query from a base condition must not modify the base"
		);
		assertNotSame(base, derived, "and() must return a new condition, not the receiver");
	}

	@Test
	@Timeout(60)
	void reusedBaseOrConditionMustNotBeMutatedByDerivedQueries()
	{
		final GigaMap<Rec> map = this.newMap();

		final Condition<Rec> base = this.f1.is("a0").or(this.f2.is("b0"));

		assertEquals(18, map.query(base).count(), "base condition (f1 OR f2)");

		// fluent AND on an OR chain binds to the chain's last clause: f1 OR (f2 AND f3)
		final Condition<Rec> derived = base.and(this.f3.is("c0"));
		assertEquals(15, map.query(derived).count(), "derived condition (f1 OR (f2 AND f3))");

		assertEquals(
			18,
			map.query(base).count(),
			"deriving a query from a base condition must not modify the base"
		);
		assertNotSame(base, derived, "and() must return a new condition, not the receiver");
	}

	@Test
	@Timeout(60)
	void indirectSelfReferenceMustNotBlowTheStack()
	{
		final GigaMap<Rec> map = this.newMap();

		final Condition<Rec> c1  = this.f1.is("a0").and(this.f2.is("b0"));
		final Condition<Rec> cOr = c1.or(this.f3.is("c0"));

		// (f1 AND f2) AND ((f1 AND f2) OR f3) == f1 AND f2
		assertEquals(6, map.query(c1.and(cOr)).count(), "condition containing the receiver");
	}

	@Test
	@Timeout(60)
	void directSelfReferenceIsIdempotent()
	{
		final GigaMap<Rec> map = this.newMap();

		final Condition<Rec> c1 = this.f1.is("a0").and(this.f2.is("b0"));

		assertEquals(6, map.query(c1.and(c1)).count(), "c AND c == c");
	}

	/**
	 * Guard rail for the documented fluent precedence semantics, which the copy-on-link fix must not change.
	 */
	@Test
	@Timeout(60)
	void fluentAndOnQueryBindsToTheLastOrClause()
	{
		final GigaMap<Rec> map = this.newMap();

		// f1 OR (f2 AND f3), since AND binds stronger than OR
		assertEquals(
			15,
			map.query(this.f1.is("a0")).or(this.f2.is("b0")).and(this.f3.is("c0")).count()
		);
	}

	/**
	 * Guard rail for the documented fluent precedence semantics, which the copy-on-link fix must not change.
	 */
	@Test
	@Timeout(60)
	void completedOrChainIsAndedAsAWhole()
	{
		final GigaMap<Rec> map = this.newMap();

		// (f1 OR f2) AND f3, since query(...) completes the OR chain into a term
		assertEquals(
			9,
			map.query(this.f1.is("a0").or(this.f2.is("b0"))).and(this.f3.is("c0")).count()
		);
	}
}
