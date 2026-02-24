package org.eclipse.store.gigamap.experimental;

/*-
 * #%L
 * EclipseStore GigaMap
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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.gigamap.types.IterationThreadProvider;
import org.eclipse.store.gigamap.types.ThreadCountProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class ThreadCountProviderTest
{
    @TempDir
    Path tempDir;


    @Test
    void threadProviderTest()
    {
        GigaMap<ThreadEntity> gigaMap           = GigaMap.New();
        StringEntityIndex     stringEntityIndex = new StringEntityIndex();
        gigaMap.index().bitmap().add(stringEntityIndex);

        fillGigaMap(gigaMap);

        final IterationThreadProvider threadProvider = IterationThreadProvider.Creating(ThreadCountProvider.Fixed(4));

        final long count = gigaMap.query(threadProvider).and(stringEntityIndex.contains("Thread")).count();
        assertEquals(1000, count, "Should find 1000 entities containing 'Thread'");
    }

    private void fillGigaMap(GigaMap<ThreadEntity> gigaMap) {
        for (int i = 0; i < 1000; i++) {
            gigaMap.add(new ThreadEntity("Thread " + i));
        }
        gigaMap.add(new ThreadEntity("Hi"));
    }

    private static class StringEntityIndex extends IndexerString.Abstract<ThreadEntity> {

        @Override
        protected String getString(ThreadEntity entity)
        {
            return entity.getName();
        }
    }

    private static class ThreadEntity {
        private final String name;

        public ThreadEntity(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "ThreadEntity{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }
}
