package test.eclipse.store.handler.other;

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

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ListStoreAllTest {

    @TempDir
    Path location;

    @Test
    public void storeAllItemsTest() {
         DataRoot dataRoot = new DataRoot();
         dataRoot.addContent(new Info("1","testName", "secondName"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(location)) {
            storageManager.setRoot(dataRoot);
            storageManager.storeRoot();


            int size = dataRoot.getList()
                    .size();
            // Init 'database' with some new data.
            for (int i = size; i < size + 2; i++) {
                Info content = new Info(String.valueOf(i), "Test:" + i, "Test");
                dataRoot.addContent(content);
            }

            storageManager.store(dataRoot.list);

            for (Info info : dataRoot.list) {
                info.setName("Changed name");
            }
            storageManager.storeAll(dataRoot.list);

            storageManager.shutdown();
        }

        try (EmbeddedStorageManager storageManager2 = EmbeddedStorage.start(location)) {
            dataRoot = (DataRoot) storageManager2.root();
            assertEquals("Changed name", dataRoot.list.get(0).name);
            assertEquals(3, dataRoot.list.size());
        }

    }


    public static class Info {
        String id;
        String name;
        String surrName;

        public Info(String id, String name, String surrName) {
            this.id = id;
            this.name = name;
            this.surrName = surrName;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class DataRoot implements Serializable {
        List<Info> list = new ArrayList<>();

        public DataRoot() {
            super();
        }

        public List<Info> getList() {
            return this.list;
        }

        public List<Info> addContent(final Info content) {
            list.add(content);
            return list;
        }

        @Override
        public String toString() {
            return "Root: " + this.list;
        }
    }
}
