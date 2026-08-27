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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.time.Duration;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs the same shutdown/start restart scenario under a matrix of storage configurations to
 * confirm that a configured storage keeps working — without data loss — across repeated stop/start
 * cycles on the same {@link EmbeddedStorageManager} instance, and that a fresh manager opened with
 * the same configuration sees the complete graph on disk.
 */
public class ConfiguredRestartTest
{
    private static final int CYCLES = 8;
    private static final int PER_CYCLE = 4;

    private static final ByteSize DATA_FILE_MIN = ByteSize.New(1, ByteUnit.KiB);
    private static final ByteSize DATA_FILE_MAX = ByteSize.New(2, ByteUnit.KiB);

    static Stream<Arguments> configurations()
    {
        return Stream.of(
                Arguments.of("default", (Consumer<EmbeddedStorageConfigurationBuilder>) b -> {
                }),
                Arguments.of("channelCount-2", cfg(b -> b.setChannelCount(2))),
                Arguments.of("channelCount-4", cfg(b -> b.setChannelCount(4))),
                Arguments.of("smallDataFiles", cfg(b -> b
                        .setDataFileMinimumSize(DATA_FILE_MIN)
                        .setDataFileMaximumSize(DATA_FILE_MAX)
                        .setDataFileMinimumUseRatio(0.9)
                        .setDataFileCleanupHeadFile(true))),
                Arguments.of("longHousekeeping", cfg(b -> b
                        .setHousekeepingInterval(Duration.ofSeconds(10))
                        .setHousekeepingTimeBudget(Duration.ofMillis(1)))),
                Arguments.of("lowEntityCache", cfg(b -> b
                        .setEntityCacheThreshold(1)
                        .setEntityCacheTimeout(Duration.ofMillis(10)))),
                // file suffixes are passed WITHOUT a leading dot — the framework inserts the '.' separator
                Arguments.of("customFileNames", cfg(b -> b
                        .setChannelDirectoryPrefix("ch_")
                        .setDataFilePrefix("dat_")
                        .setDataFileSuffix("bin")
                        .setTransactionFilePrefix("tx_")
                        .setTransactionFileSuffix("log")
                        .setTypeDictionaryFileName("types.dat")
                        .setLockFileName("store.lock"))),
                Arguments.of("combined-heavy", cfg(b -> b
                        .setChannelCount(4)
                        .setDataFileMinimumSize(DATA_FILE_MIN)
                        .setDataFileMaximumSize(DATA_FILE_MAX)
                        .setDataFileMinimumUseRatio(0.9)
                        .setHousekeepingInterval(Duration.ofSeconds(10))))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("configurations")
    @Timeout(60)
    public void configuredStorage_survivesRestartCycles(
            final String name,
            final Consumer<EmbeddedStorageConfigurationBuilder> configurer,
            @TempDir final Path baseDir
    )
    {
        final Path dir = baseDir.resolve(name);

        final List<String> root = new ArrayList<>();
        final EmbeddedStorageManager storage = newManager(dir, configurer, root);

        for (int cycle = 0; cycle < CYCLES; cycle++) {
            final List<String> live = storage.root();
            assertEquals(cycle * PER_CYCLE, live.size(), "[" + name + "] size at start of cycle " + cycle);

            for (int j = 0; j < PER_CYCLE; j++) {
                live.add("c" + cycle + "-e" + j);
            }
            storage.store(live);
            storage.shutdown();

            storage.start();

            final List<String> reloaded = storage.root();
            assertNotNull(reloaded, "[" + name + "] root after restart in cycle " + cycle);
            assertEquals((cycle + 1) * PER_CYCLE, reloaded.size(),
                    "[" + name + "] size after restart in cycle " + cycle);
        }
        storage.shutdown();

        // independent verification: a fresh manager with the SAME configuration must see the full graph
        final EmbeddedStorageManager verifier = newManager(dir, configurer, new ArrayList<String>());
        final List<String> fromDisk = verifier.root();
        assertEquals(CYCLES * PER_CYCLE, fromDisk.size(), "[" + name + "] all entries must be persisted to disk");
        for (int cycle = 0; cycle < CYCLES; cycle++) {
            for (int j = 0; j < PER_CYCLE; j++) {
                final int idx = cycle * PER_CYCLE + j;
                assertEquals("c" + cycle + "-e" + j, fromDisk.get(idx), "[" + name + "] entry " + idx + " on disk");
            }
        }
        verifier.shutdown();
    }

    @Test
    @Timeout(60)
    public void multiChannelBackup_survivesRestart_mirrors(@TempDir final Path dir, @TempDir final Path backup)
    {
        final Consumer<EmbeddedStorageConfigurationBuilder> cfg = b -> b
                .setChannelCount(4)
                .setBackupDirectory(backup.toAbsolutePath().toString());

        final List<String> root = new ArrayList<>();
        final EmbeddedStorageManager storage = newManager(dir, cfg, root);

        for (int cycle = 0; cycle < CYCLES; cycle++) {
            final List<String> live = storage.root();
            for (int j = 0; j < PER_CYCLE; j++) {
                live.add("c" + cycle + "-e" + j);
            }
            storage.store(live);
            storage.shutdown();
            storage.start();
        }
        storage.shutdown();

        // reopen the backup as primary storage (same channel count) and confirm the full graph survived
        final EmbeddedStorageManager verifier = EmbeddedStorageConfigurationBuilder.New()
                .setStorageDirectory(backup.toAbsolutePath().toString())
                .setChannelCount(4)
                .createEmbeddedStorageFoundation()
                .createEmbeddedStorageManager(new ArrayList<String>())
                .start();
        final List<String> fromBackup = verifier.root();
        assertEquals(CYCLES * PER_CYCLE, fromBackup.size(),
                "backup must contain every entry written across restart cycles");
        assertEquals("c0-e0", fromBackup.get(0));
        assertEquals("c" + (CYCLES - 1) + "-e" + (PER_CYCLE - 1), fromBackup.get(CYCLES * PER_CYCLE - 1));
        verifier.shutdown();
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
        return builder
                .createEmbeddedStorageFoundation()
                .createEmbeddedStorageManager(root)
                .start();
    }

    private static Consumer<EmbeddedStorageConfigurationBuilder> cfg(
            final Consumer<EmbeddedStorageConfigurationBuilder> c
    )
    {
        return c;
    }
}
