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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistencyDanglingReference;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageReferenceValidationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Dangling-reference validation through the GigaMap store path.
 * <p>
 * {@code BinaryHandlerGigaLevel1} stores its entity array via {@code storeReferences}, i.e. the
 * lazy {@code apply} path that skips registry-known instances. An entity whose object id is in the
 * registry but whose entity data does not exist in the storage would therefore be persisted as a
 * dangling reference inside a segment. Fail-mode validation must reject the {@code gigaMap.store()}.
 */
public class GigaMapDanglingReferenceTest
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
	void gigaMapStoreWithDanglingEntityReferenceIsRejected()
	{
		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setReferenceValidationPolicy(StorageReferenceValidationPolicy.FAIL)
					.createConfiguration()
			)
			.start();

		final GigaMap<Entity> gigaMap = this.storage.ensureRoot(GigaMap::New);
		gigaMap.add(new Entity("regular"));
		assertDoesNotThrow(() ->
		{
			gigaMap.store();
		}, "a regular GigaMap store must pass validation");

		// plant a never-stored entity in the registry under a fabricated object id, then
		// add it to the map: the segment handler's lazy apply will skip storing it.
		final long   fakeOid = DanglingRefTestUtil.FAKE_OID_BASE + 30;
		final Entity ghost   = new Entity("ghost");
		this.storage.persistenceManager().objectRegistry().registerObject(fakeOid, ghost);

		gigaMap.add(ghost);

		final RuntimeException thrown = assertThrows(
			RuntimeException.class,
			() ->
			{
				gigaMap.store();
			},
			"storing a GigaMap segment referencing a non-existing entity must be rejected"
		);
		final StorageExceptionConsistencyDanglingReference danglingReference =
			DanglingRefTestUtil.findInCauseChain(thrown, StorageExceptionConsistencyDanglingReference.class);
		assertNotNull(
			danglingReference,
			"cause chain must contain a StorageExceptionConsistencyDanglingReference, but was: " + thrown
		);
		assertArrayEquals(new long[]{fakeOid}, danglingReference.missingObjectIds());

		// restart: the rejected store must not have persisted anything of the ghost entity.
		this.storage.shutdown();
		this.storage = EmbeddedStorage.start(this.tempDir);
		@SuppressWarnings("unchecked")
		final GigaMap<Entity> reloaded = (GigaMap<Entity>)this.storage.root();
		assertNotNull(reloaded);
		assertEquals(1L, reloaded.size(), "only the regular entity may have been persisted");
		assertEquals("regular", reloaded.get(0L).data);
	}


	///////////////////////////////////////////////////////////////////////////
	// data types //
	///////////////

	public static class Entity
	{
		public String data;

		public Entity(final String data)
		{
			super();
			this.data = data;
		}
	}
}
