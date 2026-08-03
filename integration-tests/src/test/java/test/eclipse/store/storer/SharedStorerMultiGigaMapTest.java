package test.eclipse.store.storer;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coverage for {@link GigaMap#store(PersistenceStoring)} when a {@link Storer} is passed: several
 * {@link GigaMap}s registered with one {@link Storer} and committed once, which is the only way to make a
 * change spanning more than one {@link GigaMap} atomic - {@link GigaMap#store()} produces one commit per
 * map, with no atomicity between them.
 * <p>
 * The properties held here are what a caller grouping maps into one commit depends on: that
 * registration covers the entities, the indices, {@link GigaMap#set(long, Object)} replacements and
 * {@link GigaMap#update(long, java.util.function.Consumer)} mutations alike, and that nothing at all
 * reaches disk before {@link Storer#commit()}.
 */
public class SharedStorerMultiGigaMapTest
{
    static class Item
    {
        String name;

        Item(final String name)
        {
            this.name = name;
        }
    }

    static class NameIndexer extends IndexerString.Abstract<Item>
    {
        @Override
        protected String getString(final Item entity)
        {
            return entity.name;
        }
    }

    /**
     * Two maps under one root, the shape a caller collapsing several maps into one storage instance
     * ends up with.
     */
    static class Root
    {
        final GigaMap<Item> left  = GigaMap.<Item>Builder().withBitmapIndex(LEFT_NAME ).build();
        final GigaMap<Item> right = GigaMap.<Item>Builder().withBitmapIndex(RIGHT_NAME).build();
    }

    // One indexer instance per map: an indexer belongs to the index it was registered with.
    private static final NameIndexer LEFT_NAME  = new NameIndexer();
    private static final NameIndexer RIGHT_NAME = new NameIndexer();

    private static Root startAndEnsureRoot(final EmbeddedStorageManager storage)
    {
        Root root = (Root)storage.root();
        if (root == null)
        {
            root = new Root();
            storage.setRoot(root);
            storage.storeRoot();
        }
        return root;
    }

    @Test
    public void oneCommitCoversEveryRegisteredGigaMap(@TempDir final Path dir)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
        final Root                   root    = startAndEnsureRoot(storage);

        final long leftId  = root.left .add(new Item("l1"));
        final long rightId = root.right.add(new Item("r1"));

        final Storer storer = storage.createStorer();
        root.left .store(storer);
        root.right.store(storer);
        storer.commit();

        storage.shutdown();

        final EmbeddedStorageManager reopened = EmbeddedStorage.start(dir);
        final Root                   fromDisk = (Root)reopened.root();

        assertEquals(1L, fromDisk.left .size(), "left map persisted");
        assertEquals(1L, fromDisk.right.size(), "right map persisted");
        assertEquals("l1", fromDisk.left .get(leftId ).name);
        assertEquals("r1", fromDisk.right.get(rightId).name);

        // the bitmap indices rode the same commit, not just the entities
        assertEquals(1L, fromDisk.left .query(LEFT_NAME , "l1").count(), "left index persisted" );
        assertEquals(1L, fromDisk.right.query(RIGHT_NAME, "r1").count(), "right index persisted");

        reopened.shutdown();
    }

    /**
     * The negative case, and what distinguishes passing a {@link Storer} from {@link GigaMap#store()}:
     * registration alone must persist nothing. Asserted after a shutdown and a reopen from a fresh
     * manager, so it is disk state being read and not the live in-memory graph.
     */
    @Test
    public void registrationWithoutCommitPersistsNothing(@TempDir final Path dir)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
        final Root                   root    = startAndEnsureRoot(storage);

        root.left .add(new Item("l1"));
        root.right.add(new Item("r1"));

        final Storer storer = storage.createStorer();
        root.left .store(storer);
        root.right.store(storer);
        // deliberately no storer.commit()

        storage.shutdown();

        final EmbeddedStorageManager reopened = EmbeddedStorage.start(dir);
        final Root                   fromDisk = (Root)reopened.root();

        assertEquals(0L, fromDisk.left .size(), "an uncommitted registration must not reach disk");
        assertEquals(0L, fromDisk.right.size(), "an uncommitted registration must not reach disk");

        reopened.shutdown();
    }

    /**
     * A {@code set} replacement has to persist at the id it replaced, with the index re-keyed to the
     * replacement. Both halves matter: an entity that survives at the right id but keeps the old index
     * entry is reachable by {@code get} and invisible - or wrongly visible - to a query.
     */
    @Test
    public void setReplacementIsPersistedAtItsIdWithARefreshedIndex(@TempDir final Path dir)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
        final Root                   root    = startAndEnsureRoot(storage);

        final long id = root.left.add(new Item("before"));
        final Storer initial = storage.createStorer();
        root.left.store(initial);
        initial.commit();

        root.left.set(id, new Item("after"));

        final Storer storer = storage.createStorer();
        root.left.store(storer);
        storer.commit();

        storage.shutdown();

        final EmbeddedStorageManager reopened = EmbeddedStorage.start(dir);
        final Root                   fromDisk = (Root)reopened.root();

        assertEquals(1L, fromDisk.left.size(), "a replacement must not add an entity");
        assertNotNull(fromDisk.left.get(id), "the replacement kept the id it replaced");
        assertEquals("after", fromDisk.left.get(id).name, "the replacement was persisted");
        assertEquals(1L, fromDisk.left.query(LEFT_NAME, "after" ).count(), "index re-keyed to the replacement");
        assertEquals(0L, fromDisk.left.query(LEFT_NAME, "before").count(), "the replaced key is gone");

        reopened.shutdown();
    }

    /**
     * {@code update} mutates in place, so the entity keeps its object identity and only the map's
     * pending-store bookkeeping knows it changed. Registration has to carry that across.
     */
    @Test
    public void updateMutatedEntityIsPersistedWithARefreshedIndex(@TempDir final Path dir)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
        final Root                   root    = startAndEnsureRoot(storage);

        final long id = root.left.add(new Item("before"));
        final Storer initial = storage.createStorer();
        root.left.store(initial);
        initial.commit();

        root.left.update(id, item -> item.name = "after");

        final Storer storer = storage.createStorer();
        root.left.store(storer);
        storer.commit();

        storage.shutdown();

        final EmbeddedStorageManager reopened = EmbeddedStorage.start(dir);
        final Root                   fromDisk = (Root)reopened.root();

        assertEquals("after", fromDisk.left.get(id).name, "the in-place mutation was persisted");
        assertEquals(1L, fromDisk.left.query(LEFT_NAME, "after" ).count(), "index re-keyed after update");
        assertEquals(0L, fromDisk.left.query(LEFT_NAME, "before").count(), "the stale key is gone");

        reopened.shutdown();
    }

    /**
     * A removal in one map and an addition in the other, committed together - the shape of a change
     * that spans maps, which is the reason the overload exists.
     */
    @Test
    public void aChangeSpanningTwoMapsCommitsAsAUnit(@TempDir final Path dir)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
        final Root                   root    = startAndEnsureRoot(storage);

        final long doomed = root.left.add(new Item("doomed"));
        final Storer initial = storage.createStorer();
        root.left.store(initial);
        initial.commit();

        root.left.removeById(doomed);
        final long added = root.right.add(new Item("added"));

        final Storer storer = storage.createStorer();
        root.left .store(storer);
        root.right.store(storer);
        storer.commit();

        storage.shutdown();

        final EmbeddedStorageManager reopened = EmbeddedStorage.start(dir);
        final Root                   fromDisk = (Root)reopened.root();

        assertEquals(0L, fromDisk.left.size(), "the removal persisted");
        assertNull(fromDisk.left.get(doomed), "the removed entity is gone");
        assertEquals(0L, fromDisk.left.query(LEFT_NAME, "doomed").count(), "index reflects the removal");
        assertEquals("added", fromDisk.right.get(added).name, "the addition persisted");

        reopened.shutdown();
    }
}
