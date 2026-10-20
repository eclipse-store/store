package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
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

import org.eclipse.store.gigamap.jvector.migration.MigrationComputedVectorizer;
import org.eclipse.store.gigamap.jvector.migration.MigrationDoc;
import org.eclipse.store.gigamap.jvector.migration.MigrationFixtures;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexLocations;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Storages persisted before the vector file load, serve, persist the current layout and serve again after a
 * restart. The fixtures under {@code src/test/resources/migration} were generated on the last main commit
 * before the port (the 4.2.0 layout of {@code VectorIndex.Default}, with named index locations) and with the
 * 4.1.0 release (the layout without {@code structuralModCount}); each holds 300 {@link MigrationDoc} entities
 * whose vectors are {@link MigrationFixtures#position(int)}. The on-disk fixtures carry the single-file index
 * layout of graph file version 5, which the segment layout ignores.
 * <p>
 * What the migration must not do: call the vectorizer of a computed index. Its vectors were persisted in the
 * index's vector store, and the legacy handler imports them into the vector file
 * ({@link BinaryLegacyTypeHandlerVectorIndexDefault}); before the port, a restart made no vectorizer call either.
 * For a vectorizer backed by an embedding service that is one request per entity saved per migration. A
 * computed in-memory fixture has no index directory, so its vector file is temporary: the import saves the calls
 * once, and later restarts vectorize again until a directory is configured (see
 * {@link VectorIndex#keepsIndexFiles()}).
 */
class VectorIndexMigrationTest
{
    private static final Path FIXTURES = Path.of("src", "test", "resources", "migration");

    @AfterEach
    void reset()
    {
        IndexLocations.unbind(MigrationFixtures.LOCATION);
        MigrationComputedVectorizer.CALLS.set(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "embedded-inmemory-4.1.0",
        "computed-inmemory-4.1.0",
        "embedded-inmemory-4.2.0",
        "computed-inmemory-4.2.0",
        "embedded-ondisk-4.2.0",
        "computed-ondisk-4.2.0"
    })
    void fixtureLoadsServesPersistsAndRestarts(final String fixture, @TempDir final Path tempDir)
    {
        final Path    storageDir = tempDir.resolve("storage");
        final Path    indexDir   = tempDir.resolve("index");
        final boolean computed   = fixture.startsWith("computed");
        final boolean onDisk     = fixture.contains("ondisk");
        copyTree(FIXTURES.resolve(fixture), tempDir);
        if(onDisk)
        {
            IndexLocations.bind(MigrationFixtures.LOCATION, indexDir);
            assertTrue(Files.isRegularFile(indexDir.resolve(MigrationFixtures.INDEX + ".graph")),
                "precondition: the fixture carries the single-file layout");
        }

        // 1. load and serve: the legacy layout maps, the vectors come from the import, not from the vectorizer
        MigrationComputedVectorizer.CALLS.set(0);
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<MigrationDoc>             map   = rootOf(storage);
            final VectorIndex.Default<MigrationDoc> index = index(map);
            assertEquals(MigrationFixtures.COUNT, map.size());
            assertEveryEntityFound(index);
            assertNull(index.legacyVectorStore, "the imported vector store must be dropped");
            assertEquals(MigrationFixtures.COUNT, index.getExpectedVectorCount(), "the count follows the imported vectors");
            if(computed)
            {
                assertEquals(0, MigrationComputedVectorizer.CALLS.get(),
                    "a migrated computed index must not vectorize: its vectors were persisted");
            }
            // the options added since read as their defaults from a configuration persisted without them
            final VectorIndexConfiguration configuration = index.configuration();
            assertTrue(configuration.flushThreshold() > 0);
            assertTrue(configuration.maxSegments() > 0);
            assertTrue(configuration.vectorChunkBytes() > 0);

            // 2. persist the current layout: a segment beside the ignored old files, and the record without the store
            if(onDisk)
            {
                index.persistToDisk();
                assertTrue(Files.isRegularFile(indexDir.resolve(MigrationFixtures.INDEX + ".manifest")), "format 6 written");
                assertTrue(Files.isRegularFile(indexDir.resolve(MigrationFixtures.INDEX + ".graph")), "old files are left alone");
                assertTrue(Files.isRegularFile(indexDir.resolve(MigrationFixtures.INDEX + ".vec")), "the vector file lives in the index directory");
            }
            map.store();
        }

        // 3. restart on the current layout
        MigrationComputedVectorizer.CALLS.set(0);
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<MigrationDoc>             map   = rootOf(storage);
            final VectorIndex.Default<MigrationDoc> index = index(map);
            assertNull(index.legacyVectorStore, "the current layout carries no vector store");
            assertEveryEntityFound(index);
            if(onDisk)
            {
                final SourceSet set = index.sourceSet();
                assertNotNull(set);
                assertFalse(set.segments().isEmpty(), "served from the persisted segment");
            }
            if(computed && index.keepsIndexFiles())
            {
                assertEquals(0, MigrationComputedVectorizer.CALLS.get(), "the vectors come from the vector file");
            }
            else if(computed)
            {
                // no index directory: the vector file is temporary and every start vectorizes (keepsIndexFiles)
                assertEquals(MigrationFixtures.COUNT, MigrationComputedVectorizer.CALLS.get());
            }
            // a mutation after the migration works and is found
            final long added = map.add(new MigrationDoc(MigrationFixtures.COUNT));
            assertTrue(index.search(MigrationFixtures.position(MigrationFixtures.COUNT), 1).toList().stream()
                .anyMatch(e -> e.entityId() == added));
        }
    }

    @SuppressWarnings("unchecked")
    private static GigaMap<MigrationDoc> rootOf(final EmbeddedStorageManager storage)
    {
        return (GigaMap<MigrationDoc>)storage.root();
    }

    @SuppressWarnings("unchecked")
    private static VectorIndex.Default<MigrationDoc> index(final GigaMap<MigrationDoc> map)
    {
        return (VectorIndex.Default<MigrationDoc>)map.index().get(VectorIndices.class).get(MigrationFixtures.INDEX);
    }

    private static void assertEveryEntityFound(final VectorIndex<MigrationDoc> index)
    {
        for(int no = 0; no < MigrationFixtures.COUNT; no++)
        {
            final int expected = no;
            assertTrue(index.search(MigrationFixtures.position(no), 1).toList().stream()
                .anyMatch(e -> e.entity().no == expected), "entity " + no + " is not found by its own vector");
        }
    }

    private static void copyTree(final Path from, final Path to)
    {
        try(final Stream<Path> files = Files.walk(from))
        {
            for(final Path p : files.toList())
            {
                final Path target = to.resolve(from.relativize(p).toString());
                if(Files.isDirectory(p))
                {
                    Files.createDirectories(target);
                }
                else
                {
                    Files.copy(p, target);
                }
            }
        }
        catch(final IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
