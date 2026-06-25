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
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PathData implements BinaryHandlerTestData {
    Path value = Paths.get("www.microtream.one");

    // ===== proposed edge-cases (review & cherry-pick) =====
    // No edge-case fields were added. Background:
    //   BinaryHandlerPath.instanceState (persistence/binary, java/nio/file) serializes via
    //   Path.toUri().toString(). Path.toUri() for a *relative* path resolves it against the
    //   default directory first (per JDK javadoc), so every relative Path is silently turned
    //   into an absolute path with the producer's CWD prepended. Deserialization
    //   (Paths.get(URI.create(...))) reconstructs the absolute form. Net effect: relative
    //   Paths do not round-trip, and the persisted binary is CWD-dependent across machines
    //   and releases.
    //
    //   The existing assertion sidesteps this by comparing only toFile().getName() (the
    //   last segment), which survives absolutization. Probes that would assert Path.equals
    //   or toString() on relative paths cannot pass under the current handler; absolute
    //   paths in turn cannot be made portable across OS / user.dir / release for the
    //   compatibility/producer* lane. Both approaches were tried and dropped.
    //
    //   A standalone reproducer of the absolutization is in
    //   test.eclipse.store.PathRelativeRoundTripReproTest (currently @Disabled until the
    //   handler is fixed in serializer's BinaryHandlerPath).

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
