package test.eclipse.store.restservice.javalin;

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

import java.io.IOException;
import java.nio.file.Path;

import org.apache.hc.core5.http.HttpStatus;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.restservice.types.StorageRestService;
import org.eclipse.store.storage.restservice.types.StorageRestServiceResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


class EmbeddedStorageViewerStartTest {

    @TempDir
    static Path tempDir;

    @Test
    void startWithDefaults() throws IOException {
        Path first = tempDir.resolve("first");
        final EmbeddedStorageManager storage = AbstractIntegrationTest.initSourceStorage(first);
        final StorageRestService service = StorageRestServiceResolver.resolve(storage);

        service.start();

        final String url = "http://localhost:4567/store-data/root";

        AbstractIntegrationTest.checkStatusCodeForUrl(url, HttpStatus.SC_OK);

        service.stop();
        storage.shutdown();
    }

    @Test
    void startWithCustomName() throws IOException {
        Path secondPath = tempDir.resolve("second");
        final String storageName = "myMStorage";

        // Unlike the SparkJava service (which had setInstanceName()), the Javalin service reads its
        // instance name from a system property, evaluated when the service is constructed. Set it
        // before resolve() and clear it afterwards so it does not leak into other tests.
        System.setProperty("eclipse_store_rest_storage_name", storageName);
        try {
            final EmbeddedStorageManager storage = AbstractIntegrationTest.initSourceStorage(secondPath);
            final StorageRestService service = StorageRestServiceResolver.resolve(storage);

            service.start();

            final String url = "http://localhost:4567/" + storageName + "/root";

            AbstractIntegrationTest.checkStatusCodeForUrl(url, HttpStatus.SC_OK);

            service.stop();
            storage.shutdown();
        } finally {
            System.clearProperty("eclipse_store_rest_storage_name");
        }
    }
}
