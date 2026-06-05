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

import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumIndexTest
{
	enum Status
	{
		ACTIVE, INACTIVE, PENDING
	}

	static class Account
	{
		@Index Status status;

		Account(final Status status)
		{
			this.status = status;
		}
	}

	@Test
	void enumFieldProducesQueryableIndex()
	{
		final GigaMap<Account> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Account.class).generateIndices(map);

		map.add(new Account(Status.ACTIVE));
		map.add(new Account(Status.ACTIVE));
		map.add(new Account(Status.PENDING));

		final Indexer<Account, Status> index = map.index().bitmap().getIndexerForKey(Status.class, "status");
		assertEquals(Status.class, index.keyType());

		final List<Account> active = map.query(index.is(Status.ACTIVE)).toList();
		assertEquals(2, active.size());

		final List<Account> pending = map.query(index.is(Status.PENDING)).toList();
		assertEquals(1, pending.size());

		final List<Account> inactive = map.query(index.is(Status.INACTIVE)).toList();
		assertEquals(0, inactive.size());
	}
}
