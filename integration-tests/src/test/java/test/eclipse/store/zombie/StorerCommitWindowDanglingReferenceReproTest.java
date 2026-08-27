package test.eclipse.store.zombie;

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

import static org.junit.jupiter.api.Assertions.*;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.Swizzling;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageGCZombieOidHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reproducer: the lazy storer skips an already-registered
 * instance by writing its OID into the chunk without pinning it; between
 * {@link Storer#store(Object)} and {@link Storer#commit()} the storage GC can
 * delete the skipped target, so the commit persists a dangling reference that
 * surfaces as {@code StorageExceptionConsistency: No entity found for objectId}
 * after a restart.
 *
 * <p>Implements the issue's "Variant B" timeline — the
 * framework severs the reference itself, no user threading fault involved:
 * <ol>
 * <li>{@code BinaryHandlerLazyDefault.store} calls {@code Lazy.$link} during
 *     serialization, i.e. while {@code store()} builds the chunk — <b>before</b>
 *     {@code commit()}. From that moment {@link Lazy#isStored()} reports
 *     {@code true} and clearing is permitted (the {@code LazyReferenceManager}
 *     applies exactly this gate when clearing under memory pressure; the test
 *     mirrors it with an explicit guarded {@code clear()}).</li>
 * <li>Clearing drops the last strong reference to the referent; JVM GC collects
 *     it and {@code registry.cleanUp()} reaps the registry entry.</li>
 * <li>The storage GC cannot see OIDs referenced only by an uncommitted chunk
 *     (the mark seed walks the persistent root and registry-resident OIDs;
 *     skipped instances create no storer Item, so the
 *     {@code PersistenceLiveStorerRegistry} pin chain misses them too). The
 *     referent's entity — detached from the persistent root in an earlier
 *     commit — is swept, and the file check physically reclaims it.</li>
 * <li>{@code commit()} writes the chunk containing the swept OID without any
 *     validation (acknowledged FIXME priv#74, {@code StorageChannel}).</li>
 * </ol>
 *
 * <p><b>This test asserts correct behavior</b>: no zombie OID may appear and the
 * re-attached referent must be loadable after a restart. On an unfixed build it
 * fails whenever the JVM actually collects the referent inside the store→commit
 * window — the usual outcome of the explicit GC loop, but not guaranteed: if the
 * referent stays reachable (e.g. because a fixed storer pins it, or the JVM
 * declines to collect), the window never opens and the test passes without
 * exercising the defect. With a fix in place (pinning lazily-skipped targets,
 * pre-write reference validation, or post-commit {@code $link}) it passes
 * deterministically.
 *
 * <p>The companion test {@link #heldInstanceScenarioIsMitigatedBySafetyNet()}
 * documents the boundary of the defect: as long as the application holds the
 * instance, the #669 registry safety net (sweep predicate + transitive mark
 * seed, see {@code GC.md} §7–§10) protects the entity — that path does NOT
 * corrupt the storage. Only the uncommitted-chunk window does.
 */
public class StorerCommitWindowDanglingReferenceReproTest
{
    @TempDir
    Path tempDir;

    EmbeddedStorageManager storage;
    EmbeddedStorageManager reloaded;

    @AfterEach
    public void afterTest()
    {
        if (this.reloaded != null) {
            try {
                this.reloaded.shutdown();
            } catch (final Exception ignored) { /* best effort */ }
        }
        if (this.storage != null && this.storage.isRunning()) {
            try {
                this.storage.shutdown();
            } catch (final Exception ignored) { /* best effort */ }
        }
    }

    @Test
    @Timeout(120)
    void storerCommitWindowMustNotCommitDanglingReference() throws Exception
    {
        final CountingZombieOidHandler zombieHandler = new CountingZombieOidHandler();
        this.storage = this.startStorage(zombieHandler);

        final PersistenceObjectRegistry registry = this.storage.persistenceManager().objectRegistry();

        // Phase 1: store root -> Lazy -> Holder, committed.
        final DataRoot root = new DataRoot();
        Holder holder = new Holder("must survive the commit window");
        root.lazyHolder = Lazy.Reference(holder);
        this.storage.setRoot(root);
        this.storage.storeRoot();

        final long holderOid = registry.lookupObjectId(holder);
        assertNotEquals(Swizzling.notFoundId(), holderOid, "Holder must be registered after initial store");
        final WeakReference<Holder> holderProbe = new WeakReference<>(holder);

        // Phase 2: detach in a committed state. Holder is now unreachable from
        // the persistent root on disk; only the application's strong reference
        // (and the registry entry it implies) keeps its entity alive.
        root.lazyHolder = null;
        this.storage.storeRoot();

        // Phase 3: re-attach through a NEW Lazy and store via an explicit
        // storer — chunk built, NOT yet committed. BinaryHandlerLazyDefault
        // $links the new Lazy during serialization, so it may already report
        // isStored() == true although nothing has been committed.
        final Storer storer = this.storage.createLazyStorer();
        root.lazyHolder = Lazy.Reference(holder);
        storer.store(root);

        // Phase 4: the framework-sanctioned clear. This is the LazyReferenceManager
        // gate verbatim: any Lazy reporting isStored() may be cleared under memory
        // pressure. If a fix makes the uncommitted Lazy report false here, the
        // reference survives and the test passes.
        if (root.lazyHolder.isStored()) {
            root.lazyHolder.clear();
        }
        holder = null;

        if (holderProbe.get() != null) {
            for (int i = 0; i < 10 && holderProbe.get() != null; i++) {
                System.gc();
                Thread.sleep(50);
            }
            // Still strongly reachable (e.g. pinned by a fixed storer) is a PASS
            // condition, not a test-setup failure — only proceed with the GC dance
            // if the instance is really gone.
        }
        if (holderProbe.get() == null) {
            registry.cleanUp();
        }

        // Phase 5: full storage GC + file check while the chunk is still
        // uncommitted. A correct implementation must keep the entity referenced
        // by the pending commit alive (or fail the commit later).
        this.storage.issueFullGarbageCollection();
        this.storage.issueFullFileCheck();

        // Phase 6: commit the chunk. With the defect present this silently
        // persists a reference to the swept OID.
        storer.commit();

        // Correctness assertion 1, same session: the next mark cycle walks
        // root -> new Lazy -> Holder OID; no zombie may surface.
        this.storage.issueFullGarbageCollection();
        assertEquals(0, zombieHandler.count(),
                "DATA LOSS: commit persisted a dangling reference; zombie OIDs " + zombieHandler.oids()
                        + " (Holder OID " + holderOid + ")");

        // Correctness assertion 2, user-visible: restart and navigate.
        this.storage.shutdown();
        this.reloaded = EmbeddedStorage.Foundation(
                        Storage.ConfigurationBuilder()
                                .setStorageFileProvider(Storage.FileProvider(this.tempDir))
                                .createConfiguration()
                )
                .start();

        final DataRoot reloadedRoot = (DataRoot) this.reloaded.root();
        assertNotNull(reloadedRoot.lazyHolder, "reloaded root must hold the committed Lazy");
        final Holder reloadedHolder = assertDoesNotThrow(
                () -> reloadedRoot.lazyHolder.get(),
                "DATA LOSS: committed reference is dangling — referent cannot be loaded after restart");
        assertNotNull(reloadedHolder, "reloaded Holder must not be null");
        assertEquals("must survive the commit window", reloadedHolder.data,
                "reloaded Holder data must match the originally stored value");
    }

    /**
     * Boundary check: child strongly held in memory while detached on disk
     * (issue #70 cause-#1 literal recipe). Green = the #669 registry safety net
     * holds; the corruption requires the uncommitted-chunk window above.
     */
    @Test
    @Timeout(120)
    void heldInstanceScenarioIsMitigatedBySafetyNet() throws Exception
    {
        final CountingZombieOidHandler zombieHandler = new CountingZombieOidHandler();
        this.storage = this.startStorage(zombieHandler);

        final DataRoot root = new DataRoot();
        final Holder heldChild = new Holder("kept alive by the application");
        root.lazyHolder = Lazy.Reference(heldChild);
        this.storage.setRoot(root);
        this.storage.storeRoot();

        // Detach on disk, keep the instance strongly held.
        root.lazyHolder = null;
        this.storage.storeRoot();

        // Repeated full GC + file check: the sweep predicate keeps every
        // registry-resident OID, so the held child must survive.
        for (int i = 0; i < 3; i++) {
            this.storage.issueFullGarbageCollection();
            this.storage.issueFullFileCheck();
        }

        // Re-attach and store lazily — the storer skips the already-registered
        // child; its entity must still exist.
        root.lazyHolder = Lazy.Reference(heldChild);
        this.storage.storeRoot();
        this.storage.issueFullGarbageCollection();
        assertEquals(0, zombieHandler.count(),
                "safety net must prevent zombies for held instances; got " + zombieHandler.oids());

        this.storage.shutdown();
        this.reloaded = EmbeddedStorage.Foundation(
                        Storage.ConfigurationBuilder()
                                .setStorageFileProvider(Storage.FileProvider(this.tempDir))
                                .createConfiguration()
                )
                .start();

        final DataRoot reloadedRoot = (DataRoot) this.reloaded.root();
        assertNotNull(reloadedRoot.lazyHolder, "reloaded lazy must be present");
        assertEquals("kept alive by the application", reloadedRoot.lazyHolder.get().data,
                "held child must survive detach + full GC + re-attach");
    }

    private EmbeddedStorageManager startStorage(final CountingZombieOidHandler zombieHandler)
    {
        return EmbeddedStorage.Foundation(
                        Storage.ConfigurationBuilder()
                                .setChannelCountProvider(Storage.ChannelCountProvider(1))
                                .setHousekeepingController(Storage.HousekeepingController(100, 1_000_000_000))
                                .setDataFileEvaluator(Storage.DataFileEvaluator(1024, 2048, 1.0))
                                .setStorageFileProvider(Storage.FileProvider(this.tempDir))
                                .createConfiguration()
                )
                .setGCZombieOidHandler(zombieHandler)
                .start();
    }

    ///////////////////////////////////////////////////////////////////////////
    // data types //
    ////////////////

    public static class DataRoot
    {
        public Lazy<Holder> lazyHolder;
    }

    public static class Holder
    {
        public final String data;

        public Holder(final String data)
        {
            super();
            this.data = data;
        }
    }

    static final class CountingZombieOidHandler implements StorageGCZombieOidHandler
    {
        final AtomicInteger zombieCount = new AtomicInteger();
        final List<Long> zombieOids = new ArrayList<>();

        @Override
        public boolean handleZombieOid(final long objectId)
        {
            this.zombieCount.incrementAndGet();
            synchronized (this.zombieOids) {
                this.zombieOids.add(objectId);
            }
            return true;
        }

        public int count()
        {
            return this.zombieCount.get();
        }

        public List<Long> oids()
        {
            synchronized (this.zombieOids) {
                return new ArrayList<>(this.zombieOids);
            }
        }
    }
}
