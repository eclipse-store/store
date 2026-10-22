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
import org.eclipse.store.gigamap.types.IndexLocation;
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
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Storages persisted before the vector file load, serve, persist the current layout and serve again after a
 * restart. The fixtures under {@code src/test/resources/migration} were written by the 4.1.0 release (the layout
 * without {@code structuralModCount}) and the 4.2.0 release (with it; the configuration also persists
 * {@code shutdownPersistTimeoutMillis}, but none of the options added since); each holds 300 {@link MigrationDoc}
 * entities whose vectors are {@link MigrationFixtures#position(int)}. The on-disk fixtures carry the single-file
 * index layout of graph file version 5, which the segment layout ignores. 4.2.0 persists the index directory as
 * a string, here the relative {@value #INDEX_ROOT}{@code <fixture>}, which resolves against the module directory,
 * the test's working directory; the test copies the fixture's index files there.
 * <p>
 * What the migration must not do: call the vectorizer of a computed index. Its vectors were persisted in the
 * index's vector store, and the legacy handler imports them into the vector file
 * ({@link BinaryLegacyTypeHandlerVectorIndexDefault}); before the port, a restart made no vectorizer call either.
 * For a vectorizer backed by an embedding service that is one request per entity saved per migration. A
 * computed in-memory fixture has no index directory, so its vector file is temporary: storing it is refused until
 * it gets a location, which keeps the imported vectors there, so that no later restart vectorizes either (see
 * {@link VectorIndex#keepsIndexFiles()}).
 */
class VectorIndexMigrationTest
{
    private static final Path   FIXTURES   = Path.of("src", "test", "resources", "migration");
    private static final String INDEX_ROOT = "target/migration-index/";

    @AfterEach
    void reset()
    {
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
        final Path    indexDir   = Path.of(INDEX_ROOT + fixture).toAbsolutePath();
        final boolean computed   = fixture.startsWith("computed");
        final boolean onDisk     = fixture.contains("ondisk");
        copyTree(FIXTURES.resolve(fixture).resolve("storage"), storageDir);
        if(onDisk)
        {
            // where the persisted relative directory resolves; cleared of an earlier run's files first
            deleteTree(indexDir);
            copyTree(FIXTURES.resolve(fixture).resolve("index"), indexDir);
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
            assertEquals(MigrationFixtures.COUNT, index.getExpectedVectorCount(), "the count follows the imported vectors");
            if(computed && !index.keepsIndexFiles())
            {
                // The vectors are only in a temporary file: the store is kept, and storing is refused until the
                // index has a location, which keeps them without a vectorizer call.
                assertNotNull(index.legacyVectorStore, "the vector store is kept while the index has no location");
                final IllegalStateException refused = assertThrows(IllegalStateException.class, map::store);
                assertTrue(String.valueOf(rootCauseMessage(refused)).contains("changeIndexLocation"),
                    () -> "the refusal names the way out: " + rootCauseMessage(refused));
                map.index().get(VectorIndices.class).changeIndexLocation(
                    MigrationFixtures.INDEX, IndexLocation.Absolute(tempDir.resolve("vectors"))
                );
                assertTrue(index.keepsIndexFiles(), "the index keeps its vector file at the location now");
                assertEveryEntityFound(index);
            }
            assertNull(index.legacyVectorStore, "the imported vector store must be dropped");
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
            if(computed)
            {
                assertTrue(index.keepsIndexFiles(), "a computed index has a location after the migration");
                assertEquals(0, MigrationComputedVectorizer.CALLS.get(), "the vectors come from the vector file");
            }
            // a mutation after the migration works and is found
            final long added = map.add(new MigrationDoc(MigrationFixtures.COUNT));
            assertTrue(index.search(MigrationFixtures.position(MigrationFixtures.COUNT), 1).toList().stream()
                .anyMatch(e -> e.entityId() == added));
        }
    }

    private static String rootCauseMessage(final Throwable thrown)
    {
        Throwable cause = thrown;
        while(cause.getCause() != null && cause.getCause() != cause)
        {
            cause = cause.getCause();
        }
        return cause.getMessage();
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
        // A beam over every node: the fixture's vectors have many near ties, so a default beam can end in a local
        // optimum of the rebuilt graph. What this checks is that every entity is indexed with its vector.
        final int beam = MigrationFixtures.COUNT + 50;
        for(int no = 0; no < MigrationFixtures.COUNT; no++)
        {
            final int expected = no;
            assertTrue(index.search(MigrationFixtures.position(no), 1, beam).toList().stream()
                .anyMatch(e -> e.entity().no == expected), "entity " + no + " is not found by its own vector");
        }
    }

    private static void deleteTree(final Path root)
    {
        if(!Files.exists(root))
        {
            return;
        }
        try(final Stream<Path> files = Files.walk(root))
        {
            for(final Path p : files.sorted(Comparator.reverseOrder()).toList())
            {
                Files.delete(p);
            }
        }
        catch(final IOException e)
        {
            throw new UncheckedIOException(e);
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
