package test.eclipse.store.various.restart;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.eclipse.serializer.configuration.types.ByteSize;
import org.eclipse.serializer.configuration.types.ByteUnit;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Garbage collection across restarts: making objects unreachable, running a full GC, and restarting
 * must keep all still-reachable data intact, and (with a backup) the backup must remain a faithful
 * copy of the live storage after deletions are reclaimed across a restart.
 */
public class GarbageCollectionRestartTest
{
    private static final ByteSize DATA_FILE_MIN = ByteSize.New(1, ByteUnit.KiB);
    private static final ByteSize DATA_FILE_MAX = ByteSize.New(2, ByteUnit.KiB);

    private static final int INITIAL = 200;
    private static final int ADDED = 50;

    @Test
    @Timeout(60)
    public void fullGcAcrossRestart_liveDataIntact(@TempDir final Path dir)
    {
        final Consumer<EmbeddedStorageConfigurationBuilder> cfg = smallFiles();

        final List<String> root = new ArrayList<>();
        final EmbeddedStorageManager storage = newManager(dir, cfg, root);

        for (int i = 0; i < INITIAL; i++) {
            root.add("v-" + i);
        }
        storage.store(root);
        storage.shutdown();

        // drop the odd entries (make them garbage), GC, restart
        storage.start();
        final List<String> live = storage.root();
        live.removeIf(s -> Integer.parseInt(s.substring(2)) % 2 == 1);
        storage.store(live);
        storage.issueFullGarbageCollection();
        storage.issueFullFileCheck();
        storage.shutdown();

        storage.start();
        final List<String> afterGc = storage.root();
        assertEquals(INITIAL / 2, afterGc.size(), "kept (even) entries must survive GC + restart");
        for (int k = 0; k < afterGc.size(); k++) {
            assertEquals("v-" + (k * 2), afterGc.get(k), "kept entry " + k + " intact after GC");
        }

        // add more after GC, GC again, restart
        for (int i = 0; i < ADDED; i++) {
            afterGc.add("w-" + i);
        }
        storage.store(afterGc);
        storage.issueFullGarbageCollection();
        storage.shutdown();

        // fresh manager: full reachable set intact, nothing lost across the GC/restart interleaving
        final EmbeddedStorageManager fresh = newManager(dir, cfg, new ArrayList<String>());
        final List<String> fromDisk = fresh.root();
        assertEquals(INITIAL / 2 + ADDED, fromDisk.size());
        for (int k = 0; k < INITIAL / 2; k++) {
            assertEquals("v-" + (k * 2), fromDisk.get(k));
        }
        for (int i = 0; i < ADDED; i++) {
            assertEquals("w-" + i, fromDisk.get(INITIAL / 2 + i));
        }
        fresh.shutdown();
    }

    @Test
    @Timeout(60)
    public void gcWithBackup_acrossRestart_backupMirrorsLive(@TempDir final Path dir, @TempDir final Path backup)
    {
        final Consumer<EmbeddedStorageConfigurationBuilder> cfg = smallFiles()
                .andThen(b -> b.setBackupDirectory(backup.toAbsolutePath().toString()));

        final List<String> root = new ArrayList<>();
        final EmbeddedStorageManager storage = newManager(dir, cfg, root);

        for (int i = 0; i < INITIAL; i++) {
            root.add("v-" + i);
        }
        storage.store(root);
        storage.shutdown();

        // delete two thirds, GC across a restart
        storage.start();
        final List<String> live = storage.root();
        live.removeIf(s -> Integer.parseInt(s.substring(2)) % 3 != 0);
        storage.store(live);
        storage.issueFullGarbageCollection();
        storage.issueFullFileCheck();
        storage.shutdown();

        storage.start();
        storage.issueFullGarbageCollection();
        storage.issueFullFileCheck();
        final int expectedKept = storage.<List<String>>root().size();
        storage.shutdown();

        final long liveSize = totalFileSize(dir);
        final long backupSize = totalFileSize(backup);

        // reopen the backup as primary and verify it is a faithful copy of the surviving graph
        final EmbeddedStorageManager verifier = newManager(backup, smallFiles(), new ArrayList<String>());
        final List<String> fromBackup = verifier.root();
        assertEquals(expectedKept, fromBackup.size(),
                "backup must contain exactly the surviving entries after GC across restart");
        for (final String s : fromBackup) {
            assertTrue(Integer.parseInt(s.substring(2)) % 3 == 0, "only multiples of 3 survive: " + s);
        }
        verifier.shutdown();

        assertEquals(liveSize, backupSize,
                "backup must mirror live byte-for-byte after GC across restart "
                        + "(live=" + liveSize + ", backup=" + backupSize + ")");
    }

    private static Consumer<EmbeddedStorageConfigurationBuilder> smallFiles()
    {
        return b -> b
                .setChannelCount(1)
                .setDataFileMinimumSize(DATA_FILE_MIN)
                .setDataFileMaximumSize(DATA_FILE_MAX)
                .setDataFileMinimumUseRatio(0.9)
                .setDataFileCleanupHeadFile(true);
    }

    private static EmbeddedStorageManager newManager(
            final Path dir,
            final Consumer<EmbeddedStorageConfigurationBuilder> configurer,
            final List<String> root
    )
    {
        final EmbeddedStorageConfigurationBuilder builder = EmbeddedStorageConfigurationBuilder.New()
                .setStorageDirectory(dir.toAbsolutePath().toString());
        configurer.accept(builder);
        return builder.createEmbeddedStorageFoundation().createEmbeddedStorageManager(root).start();
    }

    private static long totalFileSize(final Path directory)
    {
        try (final Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile).mapToLong(p ->
            {
                try {
                    return Files.size(p);
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }).sum();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
