package one.microstream.test.configuration.notes;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - configuration
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;

class NotesConfigurationTest
{
    private static final Path FLUENT_STORAGE  = Paths.get("target", "storage-fluent");
    private static final Path FLUENT_BACKUP   = Paths.get("target", "backup-fluent");
    private static final Path INI_STORAGE     = Paths.get("target", "storage-ini");
    private static final Path INI_BACKUP      = Paths.get("target", "backup-ini");

    @Test
    void fluent_builder_round_trip() throws IOException
    {
        wipe(FLUENT_STORAGE);
        wipe(FLUENT_BACKUP);

        Note note = new Note("first", "hello via fluent", Instant.parse("2026-01-01T00:00:00Z"));

        try (NotesService svc = new NotesService(
            NotesStorageFactories.fluent(FLUENT_STORAGE, FLUENT_BACKUP)))
        {
            svc.add(note);
            svc.store();
        }

        try (NotesService svc = new NotesService(
            NotesStorageFactories.fluent(FLUENT_STORAGE, FLUENT_BACKUP)))
        {
            assertEquals(1, svc.all().size());
            Note loaded = svc.all().get(0);
            assertEquals(note.title(),     loaded.title());
            assertEquals(note.body(),      loaded.body());
            assertEquals(note.createdAt(), loaded.createdAt());
        }
    }

    @Test
    void ini_round_trip() throws IOException
    {
        wipe(INI_STORAGE);
        wipe(INI_BACKUP);

        Note note = new Note("ini-note", "hello via ini", Instant.parse("2026-02-02T00:00:00Z"));

        try (NotesService svc = new NotesService(NotesStorageFactories.ini()))
        {
            svc.add(note);
            svc.store();
        }

        try (NotesService svc = new NotesService(NotesStorageFactories.ini()))
        {
            assertEquals(1, svc.all().size());
            Note loaded = svc.all().get(0);
            assertEquals(note.title(),     loaded.title());
            assertEquals(note.body(),      loaded.body());
            assertEquals(note.createdAt(), loaded.createdAt());
        }
    }

    @Test
    void read_only_reopens_fluent_directory() throws IOException
    {
        wipe(FLUENT_STORAGE);
        wipe(FLUENT_BACKUP);

        Note seeded = new Note("ro-seed", "written via fluent", Instant.parse("2026-03-03T00:00:00Z"));

        try (NotesService svc = new NotesService(
            NotesStorageFactories.fluent(FLUENT_STORAGE, FLUENT_BACKUP)))
        {
            svc.add(seeded);
            svc.store();
        }

        EmbeddedStorageManager ro = NotesStorageFactories.readOnly(FLUENT_STORAGE);
        try (NotesService svc = new NotesService(ro))
        {
            // reads work
            assertEquals(1, svc.all().size());
            assertEquals(seeded.title(), svc.all().get(0).title());

            // a write must throw
            svc.add(new Note("forbidden", "should fail", Instant.now()));
            assertThrows(Exception.class, svc::store,
                "store() must throw under read-only mode");
        }
    }

    private static void wipe(Path dir) throws IOException
    {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir))
        {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); }
                catch (IOException e) { throw new RuntimeException(e); }
            });
        }
        assertTrue(!Files.exists(dir));
    }
}
