package one.microstream.afs.sql;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - sql-sqlite
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

import org.eclipse.store.afs.sql.types.SqlFileSystem;

import test.microstream.afs.AbstractFileSystemTest;

public abstract class SqlFileSystemTest extends AbstractFileSystemTest<SqlFileSystem> {
    protected SqlFileSystemTest() {
        super();
    }
}
