package test.microstream.library.types;

/*-
 * #%L
 * EclipseStore Integration Tests - Fixtures - Types Library
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
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PathData implements BinaryHandlerTestData {
    Path value = Paths.get("www.microtream.one");

    // edge cases
    // No edge-case fields: BinaryHandlerPath persists Path.toUri().toString(), and Path.toUri()
    // resolves a relative path against the current directory first. Relative paths therefore do
    // not round-trip and absolute paths are not portable across OS / user.dir / release, so the
    // assertion only compares the last segment (toFile().getName()).

    private static File getTempDirectory() {
        return new File(getTempDirectoryPath());
    }

    private static String getTempDirectoryPath() {
        return System.getProperty("java.io.tmpdir");
    }

    public PathData fillSampleData() {
        File file = new File("pom.xml");
        value = file.toPath();
        return this;
    }

    Path getValue() {
        return value;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        PathData copy = (PathData) o;
        assertEquals(this.getValue().toFile().getName(), copy.getValue().toFile().getName());
    }


}
