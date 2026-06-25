package test.eclipse.store.various.restart;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Type-system continuity across restarts: a type first seen in a later cycle (after one or more
 * shutdown/start cycles) must get a type handler and a non-colliding type-id, be persisted, and
 * reload correctly. Guards that {@code shutdown()}'s registry truncation does not disturb the type
 * dictionary / type-id assignment, which live in the connection foundation, not the object registry.
 */
public class TypeIntroductionRestartTest
{
	static class Alpha
	{
		String value;

		Alpha(final String value)
		{
			this.value = value;
		}
	}

	static class Beta
	{
		int number;

		Beta(final int number)
		{
			this.number = number;
		}
	}

	static class Gamma
	{
		boolean flag;
		String  label;

		Gamma(final boolean flag, final String label)
		{
			this.flag  = flag;
			this.label = label;
		}
	}

	@Test
	public void newTypesIntroducedAcrossCycles_persistAndReload(@TempDir final Path dir)
	{
		final List<Object> root = new ArrayList<>();
		final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);

		// cycle 0: only Alpha is known to the storage
		root.add(new Alpha("a-0"));
		storage.store(root);
		storage.shutdown();
		storage.start();

		// cycle 1: introduce Beta — a type the storage has never seen before this restart
		((List<Object>)storage.root()).add(new Beta(42));
		storage.store(storage.root());
		storage.shutdown();
		storage.start();

		// cycle 2: introduce Gamma — another brand-new type after a further restart
		((List<Object>)storage.root()).add(new Gamma(true, "g-2"));
		storage.store(storage.root());
		storage.shutdown();

		// fresh manager: every type introduced at a different point in the restart history must reload
		final EmbeddedStorageManager fresh = EmbeddedStorage.start(new ArrayList<Object>(), dir);
		final List<Object> fromDisk = fresh.root();
		assertEquals(3, fromDisk.size(), "all entries across all type introductions must persist");

		final Alpha alpha = assertInstanceOf(Alpha.class, fromDisk.get(0));
		assertEquals("a-0", alpha.value);

		final Beta beta = assertInstanceOf(Beta.class, fromDisk.get(1));
		assertEquals(42, beta.number);

		final Gamma gamma = assertInstanceOf(Gamma.class, fromDisk.get(2));
		assertEquals(true, gamma.flag);
		assertEquals("g-2", gamma.label);

		fresh.shutdown();
	}
}
