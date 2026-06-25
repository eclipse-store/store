package test.eclipse.store.various;

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

import org.eclipse.serializer.persistence.util.Reloader;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;


//https://github.com/eclipse-serializer/serializer/issues/135
public class ReloaderTest
{

    @TempDir
    Path workDir;

    @Test
    void reloaderTest()
    {
        ReloaderTestRoot root = new ReloaderTestRoot("Hello", "World", 42);
        try (EmbeddedStorageManager storage = EmbeddedStorage.start(root, workDir)) {
            root.lazy.clear();
            Reloader.New(storage.persistenceManager()).reloadDeep(storage.root());
        }

    }

    static class ReloaderTestRoot
    {
        private String name;
        private Lazy<String> lazy;
        private transient Integer transientField;

        public ReloaderTestRoot(String name, String lazy, Integer transientField)
        {
            this.name = name;
            this.lazy = Lazy.Reference(lazy);
            this.transientField = transientField;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public Lazy<String> getLazy()
        {
            return lazy;
        }

        public void setLazy(Lazy<String> lazy)
        {
            this.lazy = lazy;
        }

        public Integer getTransientField()
        {
            return transientField;
        }

        public void setTransientField(Integer transientField)
        {
            this.transientField = transientField;
        }
    }
}
