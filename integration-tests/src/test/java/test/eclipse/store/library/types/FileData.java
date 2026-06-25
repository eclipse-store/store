package test.eclipse.store.library.types;

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

import org.junit.jupiter.api.Assertions;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FileData implements BinaryHandlerTestData {
    // Stable default — fillSampleData and the default-constructor instance must both yield a
    // non-null value so loadDefaultCompare does not NPE on getValue().
    File value = new File("default.txt");

    // ===== proposed edge-cases (review & cherry-pick) =====
    // BinaryHandlerFile stores instance.getPath() raw and reconstructs via new File(path).
    // No absolutization (unlike BinaryHandlerPath), so any raw form survives. Probes mirror
    // the Path edge-cases that PathData couldn't assert, since here they actually work.
    // Sample data is intentionally literal (no Date.getTime() / createTempFile) so the
    // compatibility/producer* lane stays deterministic.
    private File emptyFile;
    private File relativeFile;
    private File dotsAndDoubleDots;
    private File spacesFile;
    private File unicodeFile;

    public FileData fillSampleData() {
        value = new File("test-fixture.txt");

        // ===== proposed edge-cases =====
        emptyFile = new File("");
        relativeFile = new File("a" + File.separator + "b" + File.separator + "c.txt");
        dotsAndDoubleDots = new File("a" + File.separator + "." + File.separator + "b" + File.separator + ".." + File.separator + "c.txt");
        spacesFile = new File("my dir" + File.separator + "with spaces" + File.separator + "file name.txt");
        unicodeFile = new File("Příliš" + File.separator + "kůň.txt");

        return this;
    }

    File getValue() {
        return value;
    }

    // ===== proposed edge-cases — getters =====

    public File getEmptyFile() {
        return emptyFile;
    }

    public File getRelativeFile() {
        return relativeFile;
    }

    public File getDotsAndDoubleDots() {
        return dotsAndDoubleDots;
    }

    public File getSpacesFile() {
        return spacesFile;
    }

    public File getUnicodeFile() {
        return unicodeFile;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        FileData copy = (FileData) o;
        assertAll("File tests",
                () -> assertEquals(this.getValue().getName(), copy.getValue().getName()),

                // ===== proposed edge-case verifications =====
                // File.equals compares the raw path string (case-sensitively on Unix, case-insensitively
                // on Windows). getPath() asserts the lexical form explicitly as an extra catch.
                () -> {
                    if (this.getEmptyFile() != null) {
                        assertEquals(this.getEmptyFile(), copy.getEmptyFile(), "empty File equality");
                        assertEquals("", copy.getEmptyFile().getPath(), "empty File getPath()");
                    } else {
                        assertNull(copy.getEmptyFile());
                    }
                },
                () -> {
                    if (this.getRelativeFile() != null) {
                        assertEquals(this.getRelativeFile(), copy.getRelativeFile(), "multi-segment relative File");
                        assertEquals(this.getRelativeFile().getPath(), copy.getRelativeFile().getPath(), "relative File getPath()");
                    } else {
                        assertNull(copy.getRelativeFile());
                    }
                },
                () -> {
                    if (this.getDotsAndDoubleDots() != null) {
                        // Handler must preserve "." and ".." raw — not silently normalize
                        assertEquals(this.getDotsAndDoubleDots(), copy.getDotsAndDoubleDots(), "dots File raw form preserved");
                        assertEquals(this.getDotsAndDoubleDots().getPath(), copy.getDotsAndDoubleDots().getPath(), "dots File getPath()");
                    } else {
                        assertNull(copy.getDotsAndDoubleDots());
                    }
                },
                () -> {
                    if (this.getSpacesFile() != null) {
                        assertEquals(this.getSpacesFile(), copy.getSpacesFile(), "File with spaces");
                    } else {
                        assertNull(copy.getSpacesFile());
                    }
                },
                () -> {
                    if (this.getUnicodeFile() != null) {
                        assertEquals(this.getUnicodeFile(), copy.getUnicodeFile(), "Unicode File");
                        assertEquals("kůň.txt", copy.getUnicodeFile().getName(), "Unicode filename preserved");
                    } else {
                        assertNull(copy.getUnicodeFile());
                    }
                }
        );
    }
}
