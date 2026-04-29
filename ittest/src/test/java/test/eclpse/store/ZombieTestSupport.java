package test.eclpse.store;

/*-
 * #%L
 * ittest
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

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageEntityCache;
import org.eclipse.store.storage.types.StorageGCZombieOidHandler;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared utilities for QA zombie-OID verification tests.
 * <p>
 * The test suite reproduces and probes situations where a "zombie OID" — an OID
 * referenced by a persisted binary record but whose entity has been swept from
 * the entity cache — could be produced.  Each test uses a fresh temporary
 * storage directory under the module's {@code target/} folder so runs are
 * isolated and reproducible on macOS / Linux / Windows.
 */
public final class ZombieTestSupport
{
    private ZombieTestSupport() { /* no instances */ }

    /**
     * Creates a fresh, empty working directory under {@code target/zombie-qa/<scenario>-<nanos>}.
     * The directory is deleted first if it exists.
     */
    public static Path freshWorkDir(final String scenarioName) throws IOException
    {
        final Path base = Paths.get("target", "zombie-qa");
        Files.createDirectories(base);
        final Path dir = base.resolve(scenarioName + "-" + System.nanoTime());
        deleteDirectory(dir);
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Builds a configured (but not started) foundation with the given parameters
     * and the supplied zombie OID handler installed.
     */
    public static EmbeddedStorageFoundation<?> foundation(
            final Path                     workDir,
            final int                      channelCount,
            final long                     housekeepingIntervalMs,
            final long                     housekeepingTimeBudgetNs,
            final int                      fileMin,
            final int                      fileMax,
            final double                   dissolveRatio,
            final StorageGCZombieOidHandler zombieHandler)
    {
        return EmbeddedStorage.Foundation(
                Storage.ConfigurationBuilder()
                        .setChannelCountProvider(Storage.ChannelCountProvider(channelCount))
                        .setHousekeepingController(Storage.HousekeepingController(
                                housekeepingIntervalMs, housekeepingTimeBudgetNs))
                        .setDataFileEvaluator(Storage.DataFileEvaluator(fileMin, fileMax, dissolveRatio))
                        .setStorageFileProvider(Storage.FileProvider(workDir))
                        .createConfiguration()
        ).setGCZombieOidHandler(zombieHandler);
    }

    /** Convenience: 1 channel, aggressive housekeeping (100 ms / 1 s), small files to force compaction. */
    public static EmbeddedStorageFoundation<?> defaultFoundation(
            final Path                     workDir,
            final StorageGCZombieOidHandler zombieHandler)
    {
        return foundation(workDir, 1, 100, 1_000_000_000L, 1024, 2048, 1.0, zombieHandler);
    }

    /**
     * Counting handler used by every scenario.  Records every detected zombie OID.
     */
    public static final class CountingZombieOidHandler implements StorageGCZombieOidHandler
    {
        private final AtomicInteger zombieCount = new AtomicInteger();
        private final List<Long>    zombieOids  = new ArrayList<>();
        private final String        label;

        public CountingZombieOidHandler(final String label) { this.label = label; }
        public CountingZombieOidHandler() { this("default"); }

        @Override
        public boolean handleZombieOid(final long objectId)
        {
            this.zombieCount.incrementAndGet();
            synchronized(this.zombieOids) { this.zombieOids.add(objectId); }
            System.out.println("  >>> [" + this.label + "] ZOMBIE OID: " + objectId);
            return true;
        }

        public int        count() { return this.zombieCount.get(); }
        public List<Long> oids()
        {
            synchronized(this.zombieOids)
            {
                return Collections.unmodifiableList(new ArrayList<>(this.zombieOids));
            }
        }
        public void reset()
        {
            this.zombieCount.set(0);
            synchronized(this.zombieOids) { this.zombieOids.clear(); }
        }
    }

    /**
     * Forces multiple JVM GC cycles with short pauses to give weak references a fair
     * chance of being cleared.  Not a guarantee, but reliable enough for these tests.
     */
    public static void forceJvmGc(final int rounds, final long sleepMs) throws InterruptedException
    {
        for(int i = 0; i < rounds; i++)
        {
            System.gc();
            @SuppressWarnings("unused")
            final byte[] churn = new byte[1024 * 1024];
            Thread.sleep(sleepMs);
        }
    }

    /**
     * Triggers the registry's {@code cleanUp()} via the storer merge path by storing a
     * unique throwaway object.  Returns {@code (sizeBefore, sizeAfter)}.
     */
    public static long[] triggerRegistryCleanup(final EmbeddedStorageManager storage)
    {
        final PersistenceObjectRegistry reg = storage.persistenceManager().objectRegistry();
        final long before = reg.size();
        storage.store(new String("cleanup-trigger-" + System.nanoTime()));
        final long after  = reg.size();
        return new long[] { before, after };
    }

    /**
     * Issues a full GC and waits for it to (likely) complete.
     */
    public static void runFullGc(final EmbeddedStorageManager storage, final long settleMs) throws InterruptedException
    {
        storage.issueFullGarbageCollection();
        Thread.sleep(settleMs);
    }

    /**
     * Result of a fresh-reload integrity probe.
     */
    public static final class ReloadResult
    {
        public final boolean   success;
        public final Object    root;
        public final int       zombiesOnReload;
        public final List<Long> zombieOidsOnReload;
        public final Throwable error;

        public ReloadResult(
                final boolean   success,
                final Object    root,
                final int       zombiesOnReload,
                final List<Long> zombieOidsOnReload,
                final Throwable error)
        {
            this.success            = success;
            this.root               = root;
            this.zombiesOnReload    = zombiesOnReload;
            this.zombieOidsOnReload = zombieOidsOnReload;
            this.error              = error;
        }

        @Override
        public String toString()
        {
            return "ReloadResult{success=" + this.success
                    + ", zombies=" + this.zombiesOnReload
                    + ", oids=" + this.zombieOidsOnReload
                    + (this.error != null ? ", error=" + this.error : "")
                    + "}";
        }
    }

    /**
     * Opens a fresh storage instance against {@code workDir} and runs one full GC pass.
     * Always shuts the storage down again.  Captures any startup or GC exception.
     */
    public static ReloadResult reloadAndProbe(final Path workDir) throws InterruptedException
    {
        return reloadAndProbe(workDir, 1);
    }

    /**
     * Same as {@link #reloadAndProbe(Path)} but lets the caller specify the channel
     * count that matches the on-disk layout (so multi-channel storages reload).
     */
    public static ReloadResult reloadAndProbe(final Path workDir, final int channelCount) throws InterruptedException
    {
        final CountingZombieOidHandler handler = new CountingZombieOidHandler("reload");
        EmbeddedStorageManager reloaded = null;
        Object root = null;
        Throwable err = null;
        boolean ok = false;
        try
        {
            reloaded = EmbeddedStorage.Foundation(
                    Storage.ConfigurationBuilder()
                            .setChannelCountProvider(Storage.ChannelCountProvider(channelCount))
                            .setStorageFileProvider(Storage.FileProvider(workDir))
                            .createConfiguration()
            ).setGCZombieOidHandler(handler).start();
            root = reloaded.root();
            reloaded.issueFullGarbageCollection();
            Thread.sleep(2000);
            ok = true;
        }
        catch(final Throwable t)
        {
            err = t;
        }
        finally
        {
            if(reloaded != null)
            {
                try
                {
                    StorageEntityCache.Default.setGarbageCollectionEnabled(false);
                    reloaded.shutdown();
                }
                catch(final Throwable ignored) { /* best effort */ }
            }
        }
        return new ReloadResult(ok, root, handler.count(), handler.oids(), err);
    }

    /**
     * Best-effort recursive directory deletion.
     */
    public static void deleteDirectory(final Path dir) throws IOException
    {
        if(!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult visitFile(final Path f, final BasicFileAttributes a) throws IOException
            {
                Files.delete(f);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(final Path d, final IOException e) throws IOException
            {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Pretty-prints a one-line PASS/FAIL verdict and a brief diagnostic.
     */
    public static boolean report(
            final String                   scenario,
            final CountingZombieOidHandler runHandler,
            final ReloadResult             reload,
            final String                   extraNote)
    {
        final boolean noRunZombies   = runHandler.count() == 0;
        final boolean noReloadFailure= reload.success && reload.zombiesOnReload == 0;
        final boolean pass           = noRunZombies && noReloadFailure;
        System.out.println();
        System.out.println("================ " + scenario + " ================");
        System.out.println("  zombies (run)   : " + runHandler.count() + " " + runHandler.oids());
        System.out.println("  reload success  : " + reload.success
                + (reload.error != null ? " (" + reload.error.getClass().getSimpleName()
                        + ": " + reload.error.getMessage() + ")" : ""));
        System.out.println("  zombies (reload): " + reload.zombiesOnReload + " " + reload.zombieOidsOnReload);
        if(extraNote != null) System.out.println("  note            : " + extraNote);
        System.out.println("  VERDICT         : " + (pass ? "PASS" : "FAIL"));
        System.out.println("================================================");
        return pass;
    }
}


