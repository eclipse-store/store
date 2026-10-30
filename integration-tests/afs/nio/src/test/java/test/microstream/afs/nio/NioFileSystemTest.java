package test.microstream.afs.nio;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - nio
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

import org.eclipse.store.afs.nio.types.NioFileSystem;

import test.microstream.afs.AbstractFileSystemTest;

public class NioFileSystemTest extends AbstractFileSystemTest<NioFileSystem> {

    public NioFileSystemTest() {
        super("target"); // keep the test directories out of the module root
    }

    @Override
    protected NioFileSystem setupFileSystem() {
        return NioFileSystem.New();
    }

    @Override
    protected void cleanupFileSystem() {
    }

}
