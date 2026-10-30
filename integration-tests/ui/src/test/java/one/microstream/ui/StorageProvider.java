package one.microstream.ui;

/*-
 * #%L
 * EclipseStore Integration Tests - UI
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

import org.apache.commons.io.FileUtils;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.restservice.types.StorageRestService;
import org.eclipse.store.storage.restservice.types.StorageRestServiceResolver;

import one.microstream.ui.data.BinaryHandlerTestData;

public class StorageProvider {

    static {
        // Serve the bundled Vanilla-JS viewer from the Javalin REST service (same origin as the API).
        System.setProperty("eclipse_store_rest_ui_enabled", "true");
    }

    private static EmbeddedStorageManager storage;
    private static StorageRestService service;
    private static Path tempFolder;
    private static BinaryHandlerTestData testData;


    public static void startEmptyStorage() {
        Path temp = FileUtils.getTempDirectory().toPath();
        tempFolder = temp.resolve(String.valueOf(System.nanoTime()));

        storage = EmbeddedStorage.start(tempFolder);
        service = StorageRestServiceResolver.resolve(storage);
        service.start();
    }

    public static void startStorage(BinaryHandlerTestData data) {
        testData = data;
        data.fillSampleData();

        Path temp = FileUtils.getTempDirectory().toPath();
        tempFolder = temp.resolve(String.valueOf(System.nanoTime()));

        storage = EmbeddedStorage.start(data, tempFolder);
        service = StorageRestServiceResolver.resolve(storage);
        service.start();
    }

    public static void closeStorage() throws IOException {
        if (service != null) {
            service.stop();
            service = null;
        }
        if (storage != null) {
            storage.shutdown();
            storage = null;
        }
        if (tempFolder != null) {
            FileUtils.deleteDirectory(tempFolder.toFile());
            tempFolder = null;
        }
    }

    public static void store(BinaryHandlerTestData testData) {
        storage.store(testData);
    }

    public static EmbeddedStorageManager getStorage() {
        return storage;
    }

    public static BinaryHandlerTestData getTestData() {
        return testData;
    }
}
