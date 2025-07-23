package org.eclipse.store.gigamap.restart;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

public class UpdateApiAddTest
{

    @TempDir
    Path tempDir;

    @Test
    void updateApiAddTest()
    {
        GigaMap<Element> gigaMap = GigaMap.New();
        ElementIndexer elementIndexer = new ElementIndexer();
        gigaMap.index().bitmap().add(elementIndexer);

        gigaMap.add(new Element("Element1"));
        gigaMap.add(new Element("Element2"));
        gigaMap.add(new Element("Element3"));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
        }

        GigaMap<Element> gm2 = GigaMap.New();
        gm2.add(new Element("ElementGm2"));
        gm2.add(new Element("ElementGm3"));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gm2, tempDir)) {
            Assertions.assertEquals(3, gm2.size(), "Size of the map should be equal to the size of the giga map");
            
            gm2.add(new Element("ElementGm4"));
            Assertions.assertEquals(4, gm2.size());
            Assertions.assertEquals(3, gm2.highestUsedId());
        }
    }

    private static class ElementIndexer extends IndexerString.Abstract<Element>
    {
        @Override
        protected String getString(Element entity)
        {
            return entity.getName();
        }
    }

    private static class Element {
        String name;

        public Element(String name)
        {
            this.name = name;
        }

        public String getName()
        {
            return name;
        }

        @Override
        public String toString()
        {
            return "Element{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }

}
