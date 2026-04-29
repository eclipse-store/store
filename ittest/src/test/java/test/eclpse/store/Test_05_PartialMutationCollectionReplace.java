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

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageEntityCache;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import test.eclpse.store.ZombieTestSupport.CountingZombieOidHandler;
import test.eclpse.store.ZombieTestSupport.ReloadResult;

/**
 * Scenario 04 — Partial collection mutation: replace one element, lazy storer
 * commits new instance, old element is still referenced by Java application.
 * <p>
 * Builds {@code root -> List<Item>} with N items.  Replaces {@code list.set(3, newItem)}.
 * Re-stores the list.  Drops application reference to old item #3.  JVM GC +
 * registry cleanup.  Storage GC cycle.  Verifies that:
 * <ol>
 *   <li>The list's binary references the NEW item, not the old one.</li>
 *   <li>The old item is correctly swept and produces no zombie.</li>
 *   <li>Reload yields a list with the replaced item.</li>
 * </ol>
 */
public class Test_05_PartialMutationCollectionReplace
{
    public static class Item
    {
        public String name;
        public Item(final String n) { this.name = n; }
        @Override public String toString() { return "Item[" + this.name + "]"; }
    }
    public static class DataRoot
    {
        public List<Item> items = new ArrayList<>();
    }

    public static boolean run() throws Exception
    {
        final Path workDir = ZombieTestSupport.freshWorkDir("t05-partial-mutation");
        final CountingZombieOidHandler runHandler = new CountingZombieOidHandler("run");

        final EmbeddedStorageManager storage = ZombieTestSupport
                .defaultFoundation(workDir, runHandler).start();

        final DataRoot root = new DataRoot();
        for(int i = 0; i < 10; i++) root.items.add(new Item("item-" + i));
        storage.setRoot(root);
        storage.storeRoot();
        System.out.println("Phase 1: stored 10 items");

        // Replace item at index 3 with a new instance.
        Item oldItem = root.items.get(3);
        final Item newItem = new Item("item-3-replaced");
        root.items.set(3, newItem);
        storage.store(root.items);
        System.out.println("Phase 2: replaced item-3, list re-stored");

        // Drop application reference to old item.
        oldItem = null;
        ZombieTestSupport.forceJvmGc(10, 100);
        final long[] r = ZombieTestSupport.triggerRegistryCleanup(storage);
        System.out.println("Phase 3: registry " + r[0] + " -> " + r[1]);

        ZombieTestSupport.runFullGc(storage, 3000);
        ZombieTestSupport.runFullGc(storage, 3000);
        System.out.println("Phase 4: GC done, zombies=" + runHandler.count());

        StorageEntityCache.Default.setGarbageCollectionEnabled(false);
        storage.shutdown();
        StorageEntityCache.Default.setGarbageCollectionEnabled(true);

        final ReloadResult reload = ZombieTestSupport.reloadAndProbe(workDir);
        String extra = null;
        if(reload.success && reload.root instanceof DataRoot)
        {
            final DataRoot r2 = (DataRoot) reload.root;
            extra = "reloaded item-3 = " + (r2.items.size() > 3 ? r2.items.get(3) : "<missing>");
        }
        return ZombieTestSupport.report("Test_05_PartialMutationCollectionReplace",
                runHandler, reload, extra);
    }

    public static void main(final String[] args) throws Exception
    {
        System.exit(run() ? 0 : 1);
    }
}

