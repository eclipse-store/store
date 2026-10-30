package one.microstream.test.customtypehandlers;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - custom-type-handlers
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostEntryStorageRoundTripTest
{
    @Test
    @SuppressWarnings("unchecked")
    void host_entry_roundtrip_via_embedded_storage(@TempDir final Path storageDir) throws Exception
    {
        // write
        {
            EmbeddedStorageManager storage = openStorage(storageDir, new ArrayList<HostEntry>());
            try
            {
                List<HostEntry> root = (List<HostEntry>) storage.root();
                assertNotNull(root, "initial root must be the supplied empty list");
                assertTrue(root.isEmpty(), "fresh storage starts with empty root list");

                root.add(new HostEntry("router.local", new IpAddress(0xC0A80101)));
                storage.store(root);
            }
            finally
            {
                storage.shutdown();
            }
        }

        // reopen
        {
            EmbeddedStorageManager storage = openStorage(storageDir, new ArrayList<HostEntry>());
            try
            {
                List<HostEntry> root = (List<HostEntry>) storage.root();
                assertNotNull(root, "root must be the persisted list");
                assertEquals(1, root.size(), "exactly one HostEntry restored");

                HostEntry loaded = root.get(0);
                assertNotNull(loaded.address(), "address reference restored");
                assertEquals("router.local",  loaded.hostname(),           "hostname preserved");
                assertEquals(0xC0A80101,      loaded.address().packed(),   "packed int preserved");
                assertEquals("192.168.1.1",   loaded.address().toDotted(), "dotted form preserved");

                HostEntry expected = new HostEntry("router.local", new IpAddress(0xC0A80101));
                assertEquals(expected, loaded, "value-equal after round-trip");
            }
            finally
            {
                storage.shutdown();
            }
        }
    }

    private static EmbeddedStorageManager openStorage(final Path storageDir, final Object root)
    {
        return EmbeddedStorageConfiguration.Builder()
            .setStorageDirectory(storageDir.toString())
            .createEmbeddedStorageFoundation()
            .onConnectionFoundation(cf -> {
                cf.registerCustomTypeHandler(new IpAddressHandler());
                cf.registerCustomTypeHandler(new HostEntryHandler());
            })
            .start(root);
    }
}
