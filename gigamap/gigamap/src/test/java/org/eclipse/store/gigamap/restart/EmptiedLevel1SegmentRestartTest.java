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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Emptying a complete level1 segment of an already compressed bitmap index must leave a storage that
 * can be started again.
 * <p>
 * A bitmap index's level2 segments are compressed by every store. Emptying all ids of one level1
 * segment afterwards drops that segment, which changes the persisted segment bookkeeping. If the
 * entries region is not rebuilt from the current state before the segment is written, the record's
 * length no longer matches its entries and loading the roots fails with a
 * {@code BitmapLevel2Exception}, leaving the storage unstartable.
 * <p>
 * Each test drains one complete level1 id block through a different API and then verifies both that
 * the storage restarts and that the surviving entities are still indexed exactly once - a stale
 * entries region can also resurrect the removed ids instead of failing.
 */
public class EmptiedLevel1SegmentRestartTest
{
    /**
     * Ids per level1 segment, and the corresponding shift to derive an id's segment index. A level1
     * segment covers a fixed, contiguous id range, so all ids of one block are drained by selecting
     * the ids whose {@code id >>> LEVEL1_SEGMENT_EXPONENT} is equal.
     */
    static final int LEVEL1_SEGMENT_ID_COUNT  = 4096;
    static final int LEVEL1_SEGMENT_EXPONENT  = 12;

    static final int KEY       = 1;
    static final int OTHER_KEY = 2;

    @Test
    void removeByIdOfHighestSegment(@TempDir final Path directory)
    {
        // partially filled highest segment: its entry is a compressed, non-trivial one
        this.drainAndRestart(directory, LEVEL1_SEGMENT_ID_COUNT + 904, (map, ids, victims) -> {
            ids.forEach(id -> {
                if (segmentIndex(id) == 1) {
                    map.removeById(id);
                }
            });
            return LEVEL1_SEGMENT_ID_COUNT;
        });
    }

    @Test
    void removeByIdOfCompletelyFilledHighestSegment(@TempDir final Path directory)
    {
        // completely filled highest segment: its entry is a trivial all-bits-set one
        this.drainAndRestart(directory, 2 * LEVEL1_SEGMENT_ID_COUNT, (map, ids, victims) -> {
            ids.forEach(id -> {
                if (segmentIndex(id) == 1) {
                    map.removeById(id);
                }
            });
            return LEVEL1_SEGMENT_ID_COUNT;
        });
    }

    @Test
    void updateMovingHighestSegmentToAnotherKey(@TempDir final Path directory)
    {
        // re-indexing update: the entities survive, but their bits move to another key's index entry
        this.drainAndRestart(directory, LEVEL1_SEGMENT_ID_COUNT + 904, (map, ids, victims) -> {
            ids.forEach(id -> {
                if (segmentIndex(id) == 1) {
                    map.update(id, entity -> entity.setKey(OTHER_KEY));
                }
            });
            return LEVEL1_SEGMENT_ID_COUNT;
        });
    }

    @Test
    void removeEntityOfHighestSegment(@TempDir final Path directory)
    {
        // entity based removal, i.e. the id is resolved through the index first
        this.drainAndRestart(directory, LEVEL1_SEGMENT_ID_COUNT + 904, (map, ids, victims) -> {
            victims.forEach(map::remove);
            return LEVEL1_SEGMENT_ID_COUNT;
        });
    }

    /**
     * Adds {@code entityCount} entities under {@link #KEY}, stores them (which compresses the index'
     * level2 segments), applies the passed mutation, stores again and shuts the storage down. Then
     * restarts and verifies that the expected number of entities is still indexed under {@link #KEY}.
     */
    private void drainAndRestart(final Path directory, final int entityCount, final Mutation mutation)
    {
        final long expectedRemaining;

        final GigaMap<Entity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(new KeyIndexer());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, directory)) {
            final List<Long>   ids     = new ArrayList<>(entityCount);
            final List<Entity> victims = new ArrayList<>();
            for (int i = 0; i < entityCount; i++) {
                final Entity entity = new Entity(KEY);
                final long   id     = gigaMap.add(entity);
                ids.add(id);
                if (segmentIndex(id) == 1) {
                    victims.add(entity);
                }
            }
            assertTrue(segmentIndex(ids.get(ids.size() - 1)) == 1,
                    "test setup: the ids must span exactly two level1 segments");

            // the first store compresses the index' level2 segments
            gigaMap.store();

            expectedRemaining = mutation.apply(gigaMap, ids, victims);

            // the second store persists the level2 segment whose highest level1 segment was dropped
            gigaMap.store();
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(directory)) {
            final GigaMap<Entity> restored = manager.root();
            assertEquals(expectedRemaining, restored.query(new KeyIndexer(), KEY).count(),
                    "entities indexed under the drained key after restart");
        }
    }

    private static long segmentIndex(final long entityId)
    {
        return entityId >>> LEVEL1_SEGMENT_EXPONENT;
    }

    @FunctionalInterface
    interface Mutation
    {
        /**
         * @return the number of entities expected to remain indexed under {@link #KEY}
         */
        long apply(GigaMap<Entity> map, List<Long> ids, List<Entity> highestSegmentEntities);
    }

    static class KeyIndexer extends IndexerInteger.Abstract<Entity>
    {
        @Override
        protected Integer getInteger(final Entity entity)
        {
            return entity.getKey();
        }
    }

    public static class Entity
    {
        private int key;

        public Entity(final int key)
        {
            this.key = key;
        }

        public int getKey()
        {
            return this.key;
        }

        public void setKey(final int key)
        {
            this.key = key;
        }
    }

}
