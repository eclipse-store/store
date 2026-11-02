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
import org.junit.jupiter.api.io.TempDir;

/**
 * Dangling-reference validation for the {@code Lazy} cached-id path.
 * <p>
 * When an unloaded {@code Lazy} reference is stored, its handler writes the cached object id
 * directly ({@code BinaryHandlerLazyDefault} case 3) — without consulting the object registry or
 * storing a referent. If that cached id points to a non-existing entity (e.g. left over from a
 * failed commit whose {@code $link} was never rolled back, or crash artifacts), the store would
 * silently persist a dangling reference. This test builds such a stale {@code Lazy} the same way
 * the framework's own handler constructs unloaded ones (subject {@code null}, cached id, the
 * storage's loader) and asserts that fail-mode validation rejects the store.
 */
public class LazyDanglingReferenceTest
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
	void staleLazyCachedIdIsDetectedInFailMode() throws Exception
	{
		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setReferenceValidationPolicy(StorageReferenceValidationPolicy.FAIL)
					.createConfiguration()
			)
			.start();

		// store a real Lazy first to obtain the storage's loader, the same instance
		// BinaryHandlerLazyDefault links into every Lazy it stores or loads.
		final Holder realHolder = new Holder(Lazy.Reference(new Payload("real")));
		this.storage.store(realHolder);
		final ObjectSwizzling loader = ((Lazy.Default<?>)realHolder.lazy).$getLoader();
		assertNotNull(loader, "storing must have linked the loader into the Lazy");

		// an unloaded Lazy whose cached object id points to a non-existing entity,
		// constructed exactly like the handler constructs unloaded Lazies on load.
		final long fakeOid = DanglingRefTestUtil.FAKE_OID_BASE + 20;
		final Constructor<Lazy.Default> constructor =
			Lazy.Default.class.getDeclaredConstructor(Object.class, long.class, ObjectSwizzling.class);
		constructor.setAccessible(true);
		@SuppressWarnings("unchecked")
		final Lazy<Payload> staleLazy = constructor.newInstance(null, fakeOid, loader);

		final Holder staleHolder = new Holder(staleLazy);

		final RuntimeException thrown = assertThrows(
			RuntimeException.class,
			() -> this.storage.store(staleHolder),
			"storing an unloaded Lazy with a dangling cached id must be rejected"
		);
		final StorageExceptionConsistencyDanglingReference danglingReference =
			DanglingRefTestUtil.findInCauseChain(thrown, StorageExceptionConsistencyDanglingReference.class);
		assertNotNull(
			danglingReference,
			"cause chain must contain a StorageExceptionConsistencyDanglingReference, but was: " + thrown
		);
		assertArrayEquals(new long[]{fakeOid}, danglingReference.missingObjectIds());
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
