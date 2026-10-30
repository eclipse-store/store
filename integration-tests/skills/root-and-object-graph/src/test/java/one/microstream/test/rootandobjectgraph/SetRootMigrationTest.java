package one.microstream.test.rootandobjectgraph;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - root-and-object-graph
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pattern C — switching the root class (migration).
 *
 * <p>Seed with {@link AppRoot}, then {@code setRoot(new OtherRoot())} +
 * {@code storeRoot()}. The next session opens with {@link OtherRoot} and the
 * fields round-trip.
 */
class SetRootMigrationTest
{
    @Test
    void setRoot_swapsRootClass_acrossRestart(@TempDir Path tmp)
    {
        Path storageDir = tmp.resolve("storage");

        // session 1: seed with AppRoot
        AppRoot appRoot = new AppRoot();
        EmbeddedStorageManager m1 = EmbeddedStorage.start(appRoot, storageDir);
        try
        {
            appRoot.customers().put("c1", new Customer("c1", "Alice"));
            m1.store(appRoot.customers());
        }
        finally
        {
            m1.shutdown();
        }

        // session 2: open with AppRoot, then migrate to OtherRoot
        AppRoot appRoot2 = new AppRoot();
        EmbeddedStorageManager m2 = EmbeddedStorage.start(appRoot2, storageDir);
        try
        {
            assertEquals(1, appRoot2.customers().size(),
                "AppRoot data must load before migration");

            OtherRoot migrated = new OtherRoot();
            // copy across what we want preserved
            migrated.customers().putAll(appRoot2.customers());
            migrated.notes().add("migrated-from-AppRoot");
            migrated.setLabel("v2");

            m2.setRoot(migrated);
            m2.storeRoot();
        }
        finally
        {
            m2.shutdown();
        }

        // session 3: open with OtherRoot — fields must round-trip
        OtherRoot otherRoot = new OtherRoot();
        EmbeddedStorageManager m3 = EmbeddedStorage.start(otherRoot, storageDir);
        try
        {
            assertNotNull(m3.root());
            assertEquals(1, otherRoot.customers().size(),
                "OtherRoot.customers must round-trip after migration");
            assertEquals("Alice", otherRoot.customers().get("c1").name());

            assertEquals(1, otherRoot.notes().size());
            assertEquals("migrated-from-AppRoot", otherRoot.notes().get(0));
            assertEquals("v2", otherRoot.label());
        }
        finally
        {
            m3.shutdown();
        }
    }
}
