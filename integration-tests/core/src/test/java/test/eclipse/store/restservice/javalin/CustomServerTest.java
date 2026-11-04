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
import java.net.ConnectException;
import java.nio.file.Path;

import org.apache.hc.core5.http.HttpStatus;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.restservice.types.StorageRestService;
import org.eclipse.store.storage.restservice.types.StorageRestServiceResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Javalin counterpart of the SparkJava {@code CustomServerTest}. The SparkJava service accepted a
 * caller-provided {@code spark.Service} (and thus a custom port); the Javalin service is instead
 * configured via system properties. This verifies a custom port + instance name and that the
 * service stops cleanly afterwards.
 */
class CustomServerTest {
    @TempDir
    static Path tempDir;

    @Test
    void customServerStartStopTest() throws IOException {
        final int port = 1234;
        final String storageName = "custom";
        final String url = "http://localhost:" + port + "/" + storageName + "/root";

        System.setProperty("eclipse_store_rest_port", String.valueOf(port));
        System.setProperty("eclipse_store_rest_storage_name", storageName);
        try {
            final EmbeddedStorageManager storage = AbstractIntegrationTest.initSourceStorage(tempDir);
            final StorageRestService service = StorageRestServiceResolver.resolve(storage);
            service.start();

            AbstractIntegrationTest.checkStatusCodeForUrl(url, HttpStatus.SC_OK);

            service.stop();
            storage.shutdown();

            Assertions.assertThrows(ConnectException.class, () -> AbstractIntegrationTest.get(url));
        } finally {
            System.clearProperty("eclipse_store_rest_port");
            System.clearProperty("eclipse_store_rest_storage_name");
        }
    }
}
