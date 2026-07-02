package test.eclipse.store.danglingref;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import java.util.ArrayList;
import java.util.List;

/**
 * Shared helpers for the dangling-reference validation tests.
 */
final class DanglingRefTestUtil
{
	/**
	 * A fabricated object id safely inside the OID range (which starts at one quintillion + 1)
	 * but far above anything a small test storage will ever assign.
	 */
	static final long FAKE_OID_BASE = 1_000_000_000_900_000_000L;

	/**
	 * Walks the cause chain (including suppressed exceptions) for a throwable of the given type.
	 */
	static <T extends Throwable> T findInCauseChain(final Throwable root, final Class<T> type)
	{
		final List<Throwable> queue = new ArrayList<>();
		queue.add(root);
		for(int i = 0; i < queue.size(); i++)
		{
			final Throwable current = queue.get(i);
			if(current == null)
			{
				continue;
			}
			if(type.isInstance(current))
			{
				return type.cast(current);
			}
			if(current.getCause() != null && !queue.contains(current.getCause()))
			{
				queue.add(current.getCause());
			}
			for(final Throwable suppressed : current.getSuppressed())
			{
				if(!queue.contains(suppressed))
				{
					queue.add(suppressed);
				}
			}
		}
		return null;
	}

	private DanglingRefTestUtil()
	{
		throw new UnsupportedOperationException();
	}
}
