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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.nio.file.Path;

import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.ObjectSwizzling;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistencyDanglingReference;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageReferenceValidationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Healing limits: an unloaded {@code Lazy} reference's dangling cached id has no in-memory instance
 * to re-store — the data is genuinely gone. In heal mode such a store must still fail with the
 * typed exception (like fail mode), and the storage must remain usable.
 */
@Timeout(60)
public class UnhealableLazyHealTest
{
	@TempDir
	Path tempDir;

	EmbeddedStorageManager storage;

	@AfterEach
	public void afterTest()
	{
		if(this.storage != null && this.storage.isRunning())
		{
			try
			{
				this.storage.shutdown();
			}
			catch(final Exception ignored)
			{
				// best effort
			}
		}
	}

	@Test
	void unhealableLazyCachedIdStillFailsInHealMode() throws Exception
	{
		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setReferenceValidationPolicy(StorageReferenceValidationPolicy.HEAL)
					.createConfiguration()
			)
			.start();

		// obtain the storage's loader via a properly stored Lazy (same pattern as LazyDanglingReferenceTest).
		final Holder realHolder = new Holder(Lazy.Reference(new Payload("real")));
		this.storage.store(realHolder);
		final ObjectSwizzling loader = ((Lazy.Default<?>)realHolder.lazy).$getLoader();
		assertNotNull(loader);

		// an unloaded Lazy whose cached id points to a non-existing entity: nothing to heal.
		final long fakeOid = DanglingRefTestUtil.FAKE_OID_BASE + 70;
		final Constructor<Lazy.Default> constructor =
			Lazy.Default.class.getDeclaredConstructor(Object.class, long.class, ObjectSwizzling.class);
		constructor.setAccessible(true);
		@SuppressWarnings("unchecked")
		final Lazy<Payload> staleLazy = constructor.newInstance(null, fakeOid, loader);

		final Holder staleHolder = new Holder(staleLazy);

		final RuntimeException thrown = assertThrows(
			RuntimeException.class,
			() -> this.storage.store(staleHolder),
			"an unhealable dangling Lazy cached id must still fail in heal mode"
		);
		final StorageExceptionConsistencyDanglingReference danglingReference =
			DanglingRefTestUtil.findInCauseChain(thrown, StorageExceptionConsistencyDanglingReference.class);
		assertNotNull(
			danglingReference,
			"cause chain must contain a StorageExceptionConsistencyDanglingReference, but was: " + thrown
		);
		assertArrayEquals(new long[]{fakeOid}, danglingReference.missingObjectIds());

		// the storage itself must remain usable.
		assertDoesNotThrow(() -> this.storage.store(new Payload("independent data")));
	}


	///////////////////////////////////////////////////////////////////////////
	// data types //
	///////////////

	public static class Holder
	{
		public Lazy<Payload> lazy;

		public Holder(final Lazy<Payload> lazy)
		{
			super();
			this.lazy = lazy;
		}
	}

	public static class Payload
	{
		public String data;

		public Payload(final String data)
		{
			super();
			this.data = data;
		}
	}
}
