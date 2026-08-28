package org.eclipse.store.gigamap.restart;

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

import org.eclipse.store.gigamap.types.BinaryIndexerLong;
import org.eclipse.store.gigamap.types.BitmapIndex;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Emptying a level1 segment that is <i>not</i> the highest one of its level2 segment must not
 * resurrect the removed ids.
 * <p>
 * This is the silent counterpart of {@link EmptiedLevel1SegmentRestartTest}: because the dropped
 * segment is not the highest, the persisted record's length still matches its entries, so loading
 * succeeds - but a stale entries region re-links the orphaned entry and the removed ids come back in
 * the index. Entity based queries cannot see that, since the entity slots are gone and ids are never
 * recycled, so the check goes through {@link BitmapIndex#iterateKeyEntityPairs}, which walks the
 * index' own bitmaps without resolving entities.
 */
public class DrainedMiddleSegmentRestartTest
{
    static final int LEVEL1_SEGMENT_ID_COUNT = 4096;
    static final int LEVEL1_SEGMENT_EXPONENT = 12;

    static final int SEGMENT_COUNT  = 3;
    static final int DRAINED_SEGMENT = 1;

    @Test
    void drainedIdsDoNotReappearInTheIndex(@TempDir final Path directory)
    {
        final Set<Long> drainedIds = new TreeSet<>();
        final Set<Long> keptIds    = new TreeSet<>();

        final GigaMap<Entity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(new ValueIndexer());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, directory)) {
            final List<Long> ids = new ArrayList<>(SEGMENT_COUNT * LEVEL1_SEGMENT_ID_COUNT);
            for (int i = 0; i < SEGMENT_COUNT * LEVEL1_SEGMENT_ID_COUNT; i++) {
                // every key must have at least one bit set, otherwise the entity is in no bit
                // position's bitmap at all and iterateKeyEntityPairs cannot report it
                ids.add(gigaMap.add(new Entity(i + 1)));
            }
            assertEquals(SEGMENT_COUNT - 1, segmentIndex(ids.get(ids.size() - 1)),
                    "test setup: the ids must span exactly " + SEGMENT_COUNT + " level1 segments");

            // the first store compresses the index' level2 segments
            gigaMap.store();

            ids.forEach(id -> {
                if (segmentIndex(id) == DRAINED_SEGMENT) {
                    gigaMap.removeById(id);
                    drainedIds.add(id);
                } else {
                    keptIds.add(id);
                }
            });
            assertTrue(!drainedIds.isEmpty() && !keptIds.isEmpty(), "test setup: partial removal expected");

            // the second store persists the level2 segments whose middle level1 segment was dropped
            gigaMap.store();
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(directory)) {
            final GigaMap<Entity> restored = manager.root();

            final Set<Long> indexedIds = new TreeSet<>();
            final BitmapIndex<Entity, Long> index = restored.index().bitmap().get(Long.class, "value");
            index.iterateKeyEntityPairs((key, entityId) -> indexedIds.add(entityId));

            // reported bounded: a stale entries region resurrects a whole segment, i.e. thousands of ids
            final Set<Long> resurrected = new TreeSet<>(indexedIds);
            resurrected.removeAll(keptIds);
            assertTrue(resurrected.isEmpty(), () -> resurrected.size()
                    + " removed ids are still indexed after restart, e.g. " + firstFew(resurrected));

            final Set<Long> missing = new TreeSet<>(keptIds);
            missing.removeAll(indexedIds);
            assertTrue(missing.isEmpty(), () -> missing.size()
                    + " surviving ids are missing from the index after restart, e.g. " + firstFew(missing));
        }
    }

    private static long segmentIndex(final long entityId)
    {
        return entityId >>> LEVEL1_SEGMENT_EXPONENT;
    }

    private static String firstFew(final Set<Long> ids)
    {
        final StringBuilder sb = new StringBuilder();
        int i = 0;
        for (final Long id : ids) {
            if (i++ == 5) {
                sb.append(", ...");
                break;
            }
            sb.append(i == 1 ? "" : ", ").append(id);
        }
        return sb.toString();
    }

    static class ValueIndexer extends BinaryIndexerLong.Abstract<Entity>
    {
        @Override
        public String name()
        {
            return "value";
        }

        @Override
        protected Long getLong(final Entity entity)
        {
            return entity.getValue();
        }
    }

    public static class Entity
    {
        private final long value;

        public Entity(final long value)
        {
            this.value = value;
        }

        public long getValue()
        {
            return this.value;
        }
    }

}
