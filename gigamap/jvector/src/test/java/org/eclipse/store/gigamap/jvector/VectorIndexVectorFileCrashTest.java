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


import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.persistence.types.PersistenceCommitListener;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Crash simulations for the vector file: the file is a cache that a crash may leave ahead of, behind or torn
 * against the store, and the next start must serve exactly the committed vectors, repaired from the entities.
 * <p>
 * A process crash is simulated by dropping the file without a checkpoint or a header write (its writes stay in the
 * page cache, as they would) and shutting the storage down without a store; a reboot additionally by a different
 * boot identity and by restoring chunk bytes a stale page could have left.
 */
class VectorIndexVectorFileCrashTest
{
    private static final String NAME      = "embeddings";
    private static final int    DIMENSION = 8;
    private static final UUID   THIS_BOOT  = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID   OTHER_BOOT = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    // Vectorizer calls, by entity, across all instances: the vectorizer itself is persisted with the index.
    static final List<Long>  VECTORIZED = new CopyOnWriteArrayList<>();
    // Entities for which the vectorizer breaks its contract and returns null.
    static final Set<String> BROKEN     = ConcurrentHashMap.newKeySet();
    // Whether the vectorizer allows entities without a vector, so the BROKEN ones are legitimately vector-less.
    static volatile boolean  nullable;

    static final class Doc
    {
        final String  name  ;
        final float[] vector;

        Doc(
            final String  name  ,
            final float[] vector
        )
        {
            this.name   = name  ;
            this.vector = vector;
        }
    }

    static final class DocVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            VECTORIZED.add((long)entity.name.hashCode());
            return BROKEN.contains(entity.name) ? null : entity.vector.clone();
        }

        @Override
        public boolean allowsNullVectors()
        {
            return nullable;
        }
    }

    /**
     * Pins the boot identity: only Linux provides one, and without it every restart takes the reboot path (a
     * full repair of the hot chunks), which the same-boot cases do not expect. The reboot cases override it.
     */
    @BeforeEach
    void pinBootIdentity()
    {
        VectorFile.Default.bootIdSource(() -> THIS_BOOT);
    }

    @AfterEach
    void reset()
    {
        VECTORIZED.clear();
        BROKEN.clear();
        nullable = false;
        VectorFile.Default.bootIdSource(null);
    }

    private static float[] vector(final int seed)
    {
        final float[] vector = new float[DIMENSION];
        for(int i = 0; i < DIMENSION; i++)
        {
            vector[i] = (float)Math.sin(seed * 31 + i * 7) + (i == seed % DIMENSION ? 3f : 0f);
        }
        return vector;
    }

    private static VectorIndexConfiguration config(final Path indexDirectory)
    {
        final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            // the smallest chunks allowed
            .vectorChunkBytes((long)VectorFile.MIN_SLOTS_PER_CHUNK * VectorFile.strideOf(DIMENSION))
            // a crash persists nothing; tests that need segments persist explicitly
            .persistOnShutdown(false);
        if(indexDirectory != null)
        {
            builder.onDisk(true).indexDirectory(indexDirectory);
        }
        return builder.build();
    }

    private static VectorIndex.Default<Doc> register(
        final GigaMap<Doc> map           ,
        final Path         indexDirectory
    )
    {
        return (VectorIndex.Default<Doc>)map.index().register(VectorIndices.Category())
            .add(NAME, config(indexDirectory), new DocVectorizer());
    }

    private static VectorIndex.Default<Doc> lookup(final GigaMap<Doc> map)
    {
        return (VectorIndex.Default<Doc>)map.index().get(VectorIndices.Category()).get(NAME);
    }

    private static VectorFile.Default fileOf(final VectorIndex.Default<Doc> index)
    {
        return (VectorFile.Default)index.vectorFile();
    }

    /**
     * Drops the vector file like a killed process; the caller then shuts the storage down without a store.
     */
    private static void crash(final VectorIndex.Default<Doc> index)
    {
        fileOf(index).crashForTest();
    }

    private static long topHit(
        final VectorIndex<Doc> index ,
        final float[]          vector
    )
    {
        return index.search(vector, 1).iterator().next().entityId();
    }

    private static Map<Path, byte[]> snapshotVectorFiles(final Path directory) throws IOException
    {
        final Map<Path, byte[]> files = new HashMap<>();
        try(final Stream<Path> list = Files.list(directory))
        {
            for(final Path file : (Iterable<Path>)list::iterator)
            {
                if(VectorFile.isVectorFile(NAME, file.getFileName().toString()))
                {
                    files.put(file, Files.readAllBytes(file));
                }
            }
        }
        return files;
    }

    private static void restore(
        final Path              directory,
        final Map<Path, byte[]> files
    ) throws IOException
    {
        try(final Stream<Path> list = Files.list(directory))
        {
            for(final Path file : (Iterable<Path>)list::iterator)
            {
                if(VectorFile.isVectorFile(NAME, file.getFileName().toString()))
                {
                    Files.delete(file);
                }
            }
        }
        for(final Map.Entry<Path, byte[]> file : files.entrySet())
        {
            Files.write(file.getKey(), file.getValue());
        }
    }

    @Test
    void store_commitLost_restartRepairsFromEntities(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        final long kept;
        final long added;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, indexDir);
            kept = map.add(new Doc("kept", vector(1)));
            storage.storeRoot();

            // Written to the vector file, never stored.
            map.set(kept, new Doc("kept", vector(2)));
            added = map.add(new Doc("added", vector(3)));
            crash(index);
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = lookup(map);

            assertArrayEquals(vector(1), index.getVector(kept), "the committed vector, not the lost update");
            assertNull(index.getVector(added), "an entity the store never saw has no vector");
            assertEquals(1L, index.getExpectedVectorCount());
            assertEquals(kept, topHit(index, vector(2)));
            index.close();
        }
    }

    /**
     * A custom configuration bypasses the builder's check of the chunk size; the index checks it itself, or a
     * chunk of a few slots would let sparse ids grow the chunk table without bound.
     */
    @Test
    void register_customConfigurationWithTooSmallChunk_isRejected()
    {
        final VectorIndexConfiguration valid  = config(null);
        final VectorIndexConfiguration custom = (VectorIndexConfiguration)Proxy.newProxyInstance(
            VectorIndexConfiguration.class.getClassLoader(),
            new Class<?>[]{VectorIndexConfiguration.class},
            (proxy, method, args) -> method.getName().equals("vectorChunkBytes")
                ? (Object)(long)VectorFile.strideOf(DIMENSION)
                : method.invoke(valid, args)
        );
        final GigaMap<Doc> map = GigaMap.New();

        final RuntimeException e = assertThrows(
            RuntimeException.class,
            () -> map.index().register(VectorIndices.Category()).add(NAME, custom, new DocVectorizer())
        );

        Throwable cause = e;
        while(cause != null && !(cause instanceof IllegalArgumentException))
        {
            cause = cause.getCause();
        }
        assertNotNull(cause, () -> "not rejected as an invalid argument: " + e);
        assertTrue(cause.getMessage().contains("vectorChunkBytes"), cause.getMessage());
    }

    /**
     * The vector file closes last, after the graph and the sources are torn down. A close that fails there leaves
     * the index open and re-closeable, like any failed close: the next access re-initializes it, recovers the file
     * its failed close left unclean, and answers with every vector, with or without persisted segments.
     */
    @Test
    void close_vectorFileCloseFails_nextAccessRecoversEveryVector(@TempDir final Path tempDir)
    {
        for(final boolean persisted : new boolean[]{false, true})
        {
            final GigaMap<Doc>             map   = GigaMap.New();
            final VectorIndex.Default<Doc> index = register(map, tempDir.resolve("persisted-" + persisted));
            final List<Long>               ids   = new ArrayList<>();
            for(int i = 0; i < 20; i++)
            {
                ids.add(map.add(new Doc("d" + i, vector(i))));
            }
            if(persisted)
            {
                index.persistToDisk();
            }
            VectorFile.Default.headerWriteTestHook = path ->
            {
                throw new IOException("No space left on device");
            };
            try
            {
                assertThrows(RuntimeException.class, index::close);
            }
            finally
            {
                VectorFile.Default.headerWriteTestHook = null;
            }

            // A read first, which must re-initialize the index like a search does, not report it closed.
            assertArrayEquals(vector(9), index.getVector(ids.get(9)), "persisted: " + persisted);
            assertEquals(5, index.search(vector(4), 5).size(), "persisted: " + persisted);
            assertEquals(ids.get(4), topHit(index, vector(4)), "persisted: " + persisted);
            index.close();
        }
    }

    /**
     * A store without the GigaMap's monitor writes the entity segments before the index record, and a mutation
     * can run in between. The epoch must be fixed when the GigaMap's store begins: sealed only with the record,
     * it would take in the mutation's vector, which after a crash would be trusted for an entity whose committed
     * state is the one before.
     */
    @Test
    void store_mutationBetweenSegmentsAndIndexRecord_isRepairedAfterACrash(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        final long id;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, indexDir);
            id = map.add(new Doc("a", vector(1)));
            storage.storeRoot();
            map.set(id, new Doc("a", vector(2)));

            // The racing mutation, run where the storer is about to write the index record.
            index.storeRecordTestHook = () ->
            {
                index.storeRecordTestHook = null;
                map.set(id, new Doc("a", vector(3)));
            };
            storage.storeRoot();
            crash(index);
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = lookup(map);

            assertArrayEquals(map.get(id).vector, index.getVector(id), "the vector of the committed entity");
            index.close();
        }
    }

    /**
     * The vector file shrinks with the ids it no longer holds a vector for: after a stored removeAll, the next
     * load drops the emptied chunks, and the index takes new vectors as before.
     */
    @Test
    void removeAll_storedAndReloaded_dropsTheEmptiedChunks(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, indexDir);
            for(int i = 0; i < 10; i++)
            {
                map.add(new Doc("d" + i, vector(i)));
            }
            storage.storeRoot();
            map.removeAll();
            storage.storeRoot();
            index.close();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = lookup(map);

            assertEquals(0L, index.getExpectedVectorCount());
            assertFalse(fileOf(index).hasChunk(0), "the emptied chunk is dropped");
            final long id = map.add(new Doc("new", vector(7)));
            assertEquals(id, topHit(index, vector(7)));
            index.close();
        }
    }

    @Test
    void store_doesNoFileIo(@TempDir final Path tempDir)
    {
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), tempDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, tempDir.resolve("index"));
            map.add(new Doc("a", vector(1)));
            storage.storeRoot();
            map.add(new Doc("b", vector(2)));
            final long before = fileOf(index).ioCount();

            storage.storeRoot();

            assertEquals(before, fileOf(index).ioCount(), "a store must not wait for the vector file");
            index.close();
        }
    }

    /**
     * Ordinals are entity ids, which can be sparse: a scan over the vector file must skip the chunks that were
     * never created rather than probe every ordinal up to the highest.
     */
    @Test
    void removeAll_sparseOrdinals_probesOnlyCreatedChunks()
    {
        final GigaMap<Doc>             map   = GigaMap.New();
        final VectorIndex.Default<Doc> index = register(map, null);
        map.add(new Doc("a", vector(1)));
        final VectorFile.Default file = fileOf(index);
        // A vector two thousand chunks up, as a vector of an entity with a high id would be.
        final int far = 2_000 * file.slotsPerChunk();
        file.write(far, vector(2), Vectorizer.NO_KEY);
        final LongAdder probes = new LongAdder();
        file.probeCountTestSeam = probes;

        map.removeAll();

        assertNull(file.read(far));
        assertTrue(
            probes.sum() <= 2L * file.slotsPerChunk(),
            "only the two created chunks are visited, not " + probes.sum() + " slots"
        );
        index.close();
    }

    /**
     * A store that fails after sealing never commits its epoch. The retry finds nothing written since the seal
     * and must still commit the epoch it carries, or the chunks written at it stay hot.
     */
    @Test
    void store_failedThenRetriedWithoutWrites_commitsTheCarriedEpoch(@TempDir final Path tempDir)
    {
        final GigaMap<Doc>             map   = GigaMap.New();
        // On disk: only a durable file has a header that tells hot chunks from cold ones.
        final VectorIndex.Default<Doc> index = register(map, tempDir);
        map.add(new Doc("a", vector(1)));
        final VectorFile.Default file = fileOf(index);
        final List<PersistenceCommitListener> listeners = new ArrayList<>();
        final PersistenceStoreHandler<?> handler = (PersistenceStoreHandler<?>)Proxy.newProxyInstance(
            PersistenceStoreHandler.class.getClassLoader(),
            new Class<?>[]{PersistenceStoreHandler.class},
            (proxy, method, args) ->
            {
                if(!method.getName().equals("registerCommitListener"))
                {
                    throw new UnsupportedOperationException(method.getName());
                }
                listeners.add((PersistenceCommitListener)args[0]);
                return null;
            }
        );

        index.internalSealVectorEpochOnStore(handler);
        listeners.clear(); // the store failed: its listeners never run
        index.internalSealVectorEpochOnStore(handler);
        listeners.forEach(PersistenceCommitListener::onAfterCommit);
        file.checkpoint();

        assertFalse(file.hotChunksOnDisk().get(0), "the retried store committed the epoch the chunk was written at");
        index.close();
    }

    /**
     * After a reboot a page of a hot chunk can hold an older version of a slot that looks perfectly valid. The
     * epoch scan would trust it; the reboot path repairs every ordinal of the chunk instead.
     */
    @Test
    void reboot_staleButValidSlotInHotChunk_isRepaired(@TempDir final Path tempDir) throws IOException
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        final long id;
        final Map<Path, byte[]> v1Chunks;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, indexDir);
            id = map.add(new Doc("doc", vector(1)));
            storage.storeRoot();
            fileOf(index).checkpoint();
            v1Chunks = snapshotVectorFiles(indexDir);
            v1Chunks.keySet().removeIf(path -> path.getFileName().toString().equals(NAME + ".vec"));

            map.set(id, new Doc("doc", vector(2)));
            storage.storeRoot();
            crash(index);
        }
        // The data page of v2 never reached the disk; the header, forced when the chunk turned hot, did.
        for(final Map.Entry<Path, byte[]> chunk : v1Chunks.entrySet())
        {
            Files.write(chunk.getKey(), chunk.getValue());
        }
        VectorFile.Default.bootIdSource(() -> OTHER_BOOT);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = lookup(rootOf(storage));
            assertArrayEquals(vector(2), index.getVector(id));
            assertEquals(id, topHit(index, vector(2)));
            index.close();
        }
    }

    /**
     * The vector values are bounded by the highest ordinal plus one, which must fit an int: the id that would
     * overflow it is rejected as an ordinal, the one below it is the highest accepted.
     */
    @Test
    void toOrdinal_idOfIntegerMaxValue_isRejected()
    {
        assertEquals(Integer.MAX_VALUE - 1, VectorIndex.Default.toOrdinal(Integer.MAX_VALUE - 1L));
        assertThrows(IllegalStateException.class, () -> VectorIndex.Default.toOrdinal(Integer.MAX_VALUE));
        assertThrows(IllegalStateException.class, () -> VectorIndex.Default.toOrdinal(Integer.MAX_VALUE + 1L));
    }

    /**
     * A full rebuild reports the chunk of every entity it repaired, also of one without a vector, whose chunk was
     * never created. With no vector at all the chunk table is empty, and the rebuild must still complete.
     */
    @Test
    void fullRebuild_noEntityHasAVector_completes(@TempDir final Path tempDir)
    {
        nullable = true;
        final long id;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), tempDir))
        {
            final GigaMap<Doc> map = rootOf(storage);
            register(map, null);
            BROKEN.add("none");
            id = map.add(new Doc("none", vector(1)));
            storage.storeRoot();
        }

        // An in-memory index starts every load from a fresh file, so this load rebuilds it from the entities.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
        {
            final VectorIndex.Default<Doc> index = lookup(rootOf(storage));

            assertNull(index.getVector(id));
            assertTrue(index.search(vector(1), 5).isEmpty());
            assertEquals(0L, index.getExpectedVectorCount());
            index.close();
        }
    }

    /**
     * A full rebuild writes the vectors of the entities that have one, and nothing for the ones a nullable
     * vectorizer gives none: like an add, it must not create chunks across their ids only to hold empty slots.
     */
    @Test
    void fullRebuild_vectorlessEntities_createNoChunksForThem(@TempDir final Path tempDir) throws IOException
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        nullable = true;
        final long withVector;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, indexDir);
            withVector = map.add(new Doc("with", vector(1)));
            // Three chunks' worth of entities without a vector.
            for(int i = 0; i < 3 * VectorFile.MIN_SLOTS_PER_CHUNK; i++)
            {
                BROKEN.add("none" + i);
                map.add(new Doc("none" + i, vector(i)));
            }
            storage.storeRoot();
            assertFalse(fileOf(index).hasChunk(1), "an add writes nothing for an entity without a vector");
            index.close();
        }
        restore(indexDir, Map.of());

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = lookup(rootOf(storage));

            assertArrayEquals(vector(1), index.getVector(withVector));
            assertTrue(fileOf(index).recovery().fullRebuild(), "a missing file is rebuilt from the entities");
            for(int chunk = 1; chunk <= 3; chunk++)
            {
                assertFalse(fileOf(index).hasChunk(chunk), "chunk " + chunk + " holds no vector");
            }
            assertEquals(1L, index.getExpectedVectorCount());
            index.close();
        }
    }

    @Test
    void store_commitLandedFileBehind_restartRebuildsFromEntities(@TempDir final Path tempDir) throws IOException
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        final long first;
        final long second;
        final Map<Path, byte[]> older;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, indexDir);
            first = map.add(new Doc("first", vector(1)));
            storage.storeRoot();
            index.close();
            older = snapshotVectorFiles(indexDir);
        }
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = lookup(map);
            map.set(first, new Doc("first", vector(5)));
            second = map.add(new Doc("second", vector(2)));
            storage.storeRoot();
            index.close();
        }
        restore(indexDir, older);

        VECTORIZED.clear();
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = lookup(rootOf(storage));
            assertArrayEquals(vector(5), index.getVector(first));
            assertArrayEquals(vector(2), index.getVector(second));
            assertEquals(2, VECTORIZED.size(), "every entity re-vectorized");
            assertEquals(2L, index.getExpectedVectorCount());
            index.close();
        }
    }

    @Test
    void update_crashBeforeStore_restartServesCommittedVector(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        final List<Long> ids = new ArrayList<>();
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, tempDir.resolve("index"));
            for(int i = 0; i < 40; i++)
            {
                ids.add(map.add(new Doc("d" + i, vector(i))));
            }
            storage.storeRoot();
            fileOf(index).checkpoint();
            map.set(ids.get(20), new Doc("d20", vector(99)));
            crash(index);
        }

        VECTORIZED.clear();
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = lookup(rootOf(storage));
            assertArrayEquals(vector(20), index.getVector(ids.get(20)));
            assertEquals(1, VECTORIZED.size(), "same boot: only the slot written after the store is repaired");
            assertEquals(ids.get(20), topHit(index, vector(20)));
            index.close();
        }
    }

    @Test
    void removeAll_crashBeforeStore_restartKeepsAllVectors(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        final List<Long> ids = new ArrayList<>();
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, tempDir.resolve("index"));
            for(int i = 0; i < 40; i++)
            {
                ids.add(map.add(new Doc("d" + i, vector(i))));
            }
            storage.storeRoot();
            map.removeAll();
            assertNull(index.getVector(ids.get(0)));
            crash(index);
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = lookup(rootOf(storage));
            for(int i = 0; i < ids.size(); i++)
            {
                assertArrayEquals(vector(i), index.getVector(ids.get(i)), "entity " + i);
            }
            assertEquals(40L, index.getExpectedVectorCount());
            index.close();
        }
    }

    /**
     * A storer that commits outside the monitor: the hook seals at {@code store()}, a mutation lands before the
     * commit and carries the next epoch, and a crash then leaves it ahead of the committed store.
     */
    @Test
    void writeBetweenHookAndCommit_crash_repairedFromEntity(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        final long id;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, tempDir.resolve("index"));
            id = map.add(new Doc("doc", vector(1)));
            storage.storeRoot();

            map.set(id, new Doc("doc", vector(2)));
            final Storer storer = storage.createStorer();
            storer.store(map);
            // After the hook, before the commit: not part of what this store commits.
            map.set(id, new Doc("doc", vector(3)));
            storer.commit();
            crash(index);
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = lookup(map);
            assertArrayEquals(map.get(id).vector, index.getVector(id), "the vector of the committed entity");
            assertArrayEquals(vector(2), index.getVector(id));
            index.close();
        }
    }

    @Test
    void recovery_vectorizerBreaksContract_dropsReportsAndRebuilds(@TempDir final Path tempDir) throws IOException
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        final List<Long> ids = new ArrayList<>();
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, indexDir);
            for(int i = 0; i < 30; i++)
            {
                ids.add(map.add(new Doc("d" + i, vector(i))));
            }
            index.persistToDisk();
            storage.storeRoot();
            index.close();
        }
        // The file is lost, and the vectorizer cannot reproduce two of the vectors.
        restore(indexDir, Map.of());
        BROKEN.add("d3");
        BROKEN.add("d7");

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = lookup(rootOf(storage));
            assertEquals(1, index.sourceSet().segments().size(), "precondition: the segment loaded");

            final List<Long> hits = index.search(vector(3), 30).stream().map(e -> e.entityId()).toList();

            assertEquals(28L, index.getExpectedVectorCount());
            assertNull(index.getVector(ids.get(3)));
            assertFalse(hits.contains(ids.get(3)), "a dropped vector must not answer from the stale segment");
            assertFalse(hits.contains(ids.get(7)));
            assertTrue(index.sourceSet().segments().isEmpty(), "the segment no longer matches the vectors");
            assertEquals(ids.get(4), topHit(index, vector(4)));
            index.close();
        }
    }

    /**
     * A nullable vectorizer that returns null in breach of its contract during an epoch scan drops a vector the
     * store counted, without that drop being told apart from a legitimate null: the count must follow the file.
     */
    @Test
    void recovery_nullableVectorizerBreaksContract_countFollowsTheFile(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        nullable = true;
        final List<Long> ids = new ArrayList<>();
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, indexDir);
            for(int i = 0; i < 30; i++)
            {
                ids.add(map.add(new Doc("d" + i, vector(i))));
            }
            storage.storeRoot();
            // Written to the vector file, never stored: the epoch scan repairs this slot.
            map.set(ids.get(3), new Doc("d3", vector(50)));
            crash(index);
        }
        BROKEN.add("d3");

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = lookup(rootOf(storage));

            assertNull(index.getVector(ids.get(3)));
            assertFalse(fileOf(index).recovery().fullRebuild(), "precondition: an epoch scan");
            assertEquals(29L, index.getExpectedVectorCount());
            index.close();
        }
    }

    @Test
    void recovery_repairsInAscendingOrdinalOrder(@TempDir final Path tempDir) throws IOException
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        final List<Long> expected = new ArrayList<>();
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, indexDir);
            for(int i = 0; i < 50; i++)
            {
                map.add(new Doc("d" + i, vector(i)));
                expected.add((long)("d" + i).hashCode());
            }
            storage.storeRoot();
            index.close();
        }
        restore(indexDir, Map.of());

        VECTORIZED.clear();
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = lookup(rootOf(storage));
            index.getVector(0L);
            assertEquals(expected, VECTORIZED);
            index.close();
        }
    }

    @Test
    void recovery_onDiskSegmentsLoaded_runsBeforeFirstSearch(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        final List<Long> ids = new ArrayList<>();
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, tempDir.resolve("index"));
            for(int i = 0; i < 30; i++)
            {
                ids.add(map.add(new Doc("d" + i, vector(i))));
            }
            index.persistToDisk();
            storage.storeRoot();
            // Ahead of the store, in a chunk the segment's exact rerank reads.
            map.set(ids.get(5), new Doc("d5", vector(77)));
            crash(index);
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = lookup(rootOf(storage));
            assertEquals(1, index.sourceSet().segments().size());

            final float score5 = index.search(vector(5), 1).iterator().next().score();
            assertEquals(ids.get(5), topHit(index, vector(5)));
            assertArrayEquals(vector(5), index.getVector(ids.get(5)));
            assertNotEquals(ids.get(5), topHit(index, vector(77)));
            assertTrue(score5 > 0.99f, "reranked against the repaired vector, scored " + score5);
            index.close();
        }
    }

    /**
     * An eventual-indexing add still queued at the freeze moves into the frozen graph; removing the entity after the
     * freeze changes the vector file, but the segment must hold the vector the witnesses sampled at the freeze count.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void freeze_movedAddThenRemove_segmentHoldsEntityAtWitness(@TempDir final Path tempDir) throws Exception
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .onDisk(true)
            .indexDirectory(tempDir)
            .eventualIndexing(true)
            .build();
        final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)map.index()
            .register(VectorIndices.Category()).add(NAME, config, new DocVectorizer());
        try
        {
            for(int i = 0; i < 20; i++)
            {
                map.add(new Doc("d" + i, vector(i)));
            }
            index.backgroundTaskManager.drainQueue();

            // Hold the worker in its next operation, so the add behind it is still queued at the freeze.
            final CountDownLatch applying = new CountDownLatch(1);
            final CountDownLatch release  = new CountDownLatch(1);
            index.backgroundTaskManager.applyTestHook = () ->
            {
                index.backgroundTaskManager.applyTestHook = null;
                applying.countDown();
                awaitQuietly(release);
            };
            map.add(new Doc("held", vector(30)));
            awaitQuietly(applying);
            final long moved = map.add(new Doc("moved", vector(31)));

            final CountDownLatch frozen  = new CountDownLatch(1);
            final CountDownLatch removed = new CountDownLatch(1);
            index.flushStageTestHook = stage ->
            {
                if(stage == VectorIndex.Default.FlushStage.FROZEN)
                {
                    frozen.countDown();
                    awaitQuietly(removed);
                }
            };
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final Thread persist = new Thread(() ->
            {
                try
                {
                    index.persistToDisk();
                }
                catch(final Throwable t)
                {
                    failure.set(t);
                }
            }, "persist");
            persist.start();
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while(persist.getState() != Thread.State.WAITING && frozen.getCount() > 0)
            {
                assertTrue(System.nanoTime() < deadline, "the persist must wait for the indexing lock");
                Thread.onSpinWait();
            }
            release.countDown();
            awaitQuietly(frozen);
            map.removeById(moved);
            assertNull(index.getVector(moved), "the file no longer has it");
            removed.countDown();
            persist.join(TimeUnit.SECONDS.toMillis(60));
            index.flushStageTestHook = null;
            assertNull(failure.get(), () -> "persist failed: " + failure.get());

            final DiskSegment segment = index.sourceSet().segments().get(0);
            final int[] l2g = segment.localToGlobal();
            int local = -1;
            for(int i = 0; i < l2g.length; i++)
            {
                if(l2g[i] == (int)moved)
                {
                    local = i;
                }
            }
            assertTrue(local >= 0, "the moved add is in the segment");
            try(final var view = segment.graph().getView())
            {
                final float[] stored = new float[DIMENSION];
                final var     vector = view.getVector(local);
                for(int i = 0; i < DIMENSION; i++)
                {
                    stored[i] = vector.get(i);
                }
                assertArrayEquals(vector(31), stored, "the segment holds the vector the freeze saw");
            }
        }
        finally
        {
            index.close();
        }
    }

    /**
     * A node the frozen graph held before the freeze, updated and another removed after it: the segment the flush
     * writes under the freeze-time witnesses must hold their freeze-time vectors, not the file's newer ones.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void freeze_appliedNodesMutatedAfterFreeze_segmentHoldsFreezeTimeVectors(@TempDir final Path tempDir)
        throws Exception
    {
        final GigaMap<Doc>             map   = GigaMap.New();
        final VectorIndex.Default<Doc> index = register(map, tempDir);
        try
        {
            final List<Long> ids = new ArrayList<>();
            for(int i = 0; i < 20; i++)
            {
                ids.add(map.add(new Doc("d" + i, vector(i))));
            }
            final long updated = ids.get(3);
            final long removed = ids.get(7);

            final CountDownLatch frozen  = new CountDownLatch(1);
            final CountDownLatch mutated = new CountDownLatch(1);
            index.flushStageTestHook = stage ->
            {
                if(stage == VectorIndex.Default.FlushStage.FROZEN)
                {
                    frozen.countDown();
                    awaitQuietly(mutated);
                }
            };
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final Thread persist = new Thread(() ->
            {
                try
                {
                    index.persistToDisk();
                }
                catch(final Throwable t)
                {
                    failure.set(t);
                }
            }, "persist");
            persist.start();
            awaitQuietly(frozen);
            map.set(updated, new Doc("d3", vector(99)));
            map.removeById(removed);
            mutated.countDown();
            persist.join(TimeUnit.SECONDS.toMillis(60));
            index.flushStageTestHook = null;
            assertNull(failure.get(), () -> "persist failed: " + failure.get());

            final DiskSegment segment = index.sourceSet().segments().get(0);
            assertArrayEquals(vector(3), segmentVector(segment, updated), "the freeze-time vector, not the update");
            assertArrayEquals(vector(7), segmentVector(segment, removed), "the freeze-time vector, not a placeholder");
            assertArrayEquals(vector(99), index.getVector(updated), "the file has moved on");
        }
        finally
        {
            index.close();
        }
    }

    /**
     * A flush publishes on its own thread, not under the monitor a mutation holds. A mutation that preserves
     * freeze-time vectors must hold the set it reads the frozen generations from, or the flush can release the
     * set and close a generation while the mutation still reads it.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void preserve_flushPublishesMeanwhile_generationStaysOpenUntilDone(@TempDir final Path tempDir)
        throws Exception
    {
        final GigaMap<Doc>             map   = GigaMap.New();
        final VectorIndex.Default<Doc> index = register(map, tempDir);
        try
        {
            final List<Long> ids = new ArrayList<>();
            for(int i = 0; i < 20; i++)
            {
                ids.add(map.add(new Doc("d" + i, vector(i))));
            }

            final AtomicReference<HeapGeneration> generation = new AtomicReference<>();
            final CountDownLatch                  frozen     = new CountDownLatch(1);
            final CountDownLatch                  mutating   = new CountDownLatch(1);
            final CountDownLatch                  published  = new CountDownLatch(1);
            index.flushStageTestHook = stage ->
            {
                if(stage == VectorIndex.Default.FlushStage.FROZEN)
                {
                    generation.set(index.sourceSet().frozen().get(0));
                    frozen.countDown();
                    awaitQuietly(mutating);
                }
                else if(stage == VectorIndex.Default.FlushStage.PUBLISHED)
                {
                    published.countDown();
                }
            };
            final AtomicReference<Boolean> closedWhilePreserving = new AtomicReference<>();
            index.preserveTestHook = () ->
            {
                index.preserveTestHook = null;
                // The flush runs to its publication while this mutation holds the monitor mid-preservation.
                mutating.countDown();
                awaitQuietly(published);
                closedWhilePreserving.set(generation.get().isClosed());
            };
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final Thread persist = new Thread(() ->
            {
                try
                {
                    index.persistToDisk();
                }
                catch(final Throwable t)
                {
                    failure.set(t);
                }
            }, "persist");
            persist.start();
            awaitQuietly(frozen);

            map.set(ids.get(3), new Doc("d3", vector(99)));
            persist.join(TimeUnit.SECONDS.toMillis(60));
            index.flushStageTestHook = null;

            assertNull(failure.get(), () -> "persist failed: " + failure.get());
            assertEquals(Boolean.FALSE, closedWhilePreserving.get(), "the generation closed under the mutation");
            assertTrue(generation.get().isClosed(), "released once the mutation is done with it");
        }
        finally
        {
            index.close();
        }
    }

    private static float[] segmentVector(
        final DiskSegment segment,
        final long        entityId
    ) throws IOException
    {
        final int[] l2g   = segment.localToGlobal();
        int         local = -1;
        for(int i = 0; i < l2g.length; i++)
        {
            if(l2g[i] == (int)entityId)
            {
                local = i;
            }
        }
        assertTrue(local >= 0, "entity " + entityId + " is in the segment");
        try(final var view = segment.graph().getView())
        {
            final var     vector = view.getVector(local);
            final float[] stored = new float[DIMENSION];
            for(int i = 0; i < DIMENSION; i++)
            {
                stored[i] = vector.get(i);
            }
            return stored;
        }
    }

    private static void awaitQuietly(final CountDownLatch latch)
    {
        try
        {
            if(!latch.await(60, TimeUnit.SECONDS))
            {
                throw new IllegalStateException("latch timed out");
            }
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void inMemory_restart_rebuildsIntoFreshTempDirectory(@TempDir final Path tempDir)
    {
        final Path firstDirectory;
        final long id;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), tempDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = register(map, null);
            id = map.add(new Doc("doc", vector(1)));
            storage.storeRoot();
            firstDirectory = vectorDirectoryOf(index);
            index.close();
            assertFalse(Files.exists(firstDirectory), "an in-memory index's file goes with the close");
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
        {
            final VectorIndex.Default<Doc> index = lookup(rootOf(storage));
            assertArrayEquals(vector(1), index.getVector(id));
            assertNotEquals(firstDirectory, vectorDirectoryOf(index));
            assertEquals(id, topHit(index, vector(1)));
            index.close();
        }
    }

    private static Path vectorDirectoryOf(final VectorIndex.Default<Doc> index)
    {
        final String description = index.vectorFile().toString();
        final String path        = description.substring(description.indexOf(", ") + 2, description.length() - 1);
        return Path.of(path).getParent();
    }

    @Test
    void reapUnlistedFiles_keepsVectorFiles(@TempDir final Path tempDir) throws IOException
    {
        final GigaMap<Doc>             map   = GigaMap.New();
        final VectorIndex.Default<Doc> index = register(map, tempDir);
        try
        {
            final long id = map.add(new Doc("a", vector(1)));
            index.persistToDisk();
            map.add(new Doc("b", vector(2)));
            index.persistToDisk();
            index.diskIndexManager().reapUnlistedFiles(Set.of());

            assertFalse(snapshotVectorFiles(tempDir).isEmpty());
            assertArrayEquals(vector(1), index.getVector(id));
        }
        finally
        {
            index.close();
        }
    }

    /**
     * The root as the typed map: the storage manager is not generic over the root.
     */
    @SuppressWarnings("unchecked")
    private static GigaMap<Doc> rootOf(final EmbeddedStorageManager storage)
    {
        return (GigaMap<Doc>)storage.root();
    }
}
