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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that {@link Lazy} references survive a shutdown/start cycle on the same
 * {@link EmbeddedStorageManager} instance and can be (re)loaded from disk afterwards.
 *
 * <p>This directly exercises the restart fix in {@code EmbeddedStorageBinarySource}, which now
 * resolves the current {@code StorageRequestAcceptor} via a supplier on every read: a lazy load
 * triggered after a restart must go through the storage's <em>new</em> request acceptor, not a
 * stale one captured before shutdown.
 */
public class LazyRestartTest
{
	static class Payload
	{
		String data;
		int    value;

		Payload(final String data, final int value)
		{
			this.data  = data;
			this.value = value;
		}
	}

	static class Holder
	{
		List<Lazy<Payload>> items = new ArrayList<>();
	}

	private static final int COUNT = 10;

	@Test
	public void lazyRefs_reloadFromDisk_afterRestart(@TempDir final Path dir)
	{
		final Holder holder = new Holder();
		final EmbeddedStorageManager storage = EmbeddedStorage.start(holder, dir);

		for(int i = 0; i < COUNT; i++)
		{
			holder.items.add(Lazy.Reference(new Payload("payload-" + i, i)));
		}
		// store the mutated list (not just the root) so the added lazy refs are persisted
		storage.store(holder.items);
		storage.shutdown();

		storage.start();

		final Holder reloaded = storage.root();
		assertNotNull(reloaded);
		assertEquals(COUNT, reloaded.items.size());

		for(int i = 0; i < COUNT; i++)
		{
			final Lazy<Payload> lazy = reloaded.items.get(i);

			// force the referent out of memory, then pull it back in — this load must be served
			// by the post-restart request acceptor (the supplier self-heal path)
			Lazy.clear(lazy);
			assertFalse(Lazy.isLoaded(lazy), "reference must be unloaded after clear (i=" + i + ")");

			final Payload p = lazy.get();
			assertNotNull(p, "lazy.get() must load from disk after restart (i=" + i + ")");
			assertEquals("payload-" + i, p.data);
			assertEquals(i, p.value);
		}

		storage.shutdown();
	}

	@Test
	public void lazyRefs_mutatedAcrossCycles_persist(@TempDir final Path dir)
	{
		final Holder holder = new Holder();
		holder.items.add(Lazy.Reference(new Payload("v0", 0)));

		final EmbeddedStorageManager storage = EmbeddedStorage.start(holder, dir);
		storage.storeRoot();
		storage.shutdown();

		// three cycles: each time load the lazy referent, mutate it, store, restart
		for(int cycle = 1; cycle <= 3; cycle++)
		{
			storage.start();
			final Holder reloaded = storage.root();
			final Lazy<Payload> lazy = reloaded.items.get(0);
			final Payload p = lazy.get();
			p.data  = "v" + cycle;
			p.value = cycle;
			storage.store(p);
			storage.shutdown();
		}

		// independent verification from a fresh manager
		final EmbeddedStorageManager verifier = EmbeddedStorage.start(new Holder(), dir);
		final Holder fromDisk = verifier.root();
		final Payload p = fromDisk.items.get(0).get();
		assertEquals("v3", p.data, "last mutation across cycles must be on disk");
		assertEquals(3, p.value);
		verifier.shutdown();
	}
}
