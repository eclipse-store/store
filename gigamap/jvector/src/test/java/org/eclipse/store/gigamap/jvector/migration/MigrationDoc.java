package org.eclipse.store.gigamap.jvector.migration;

/*-
 * #%L
 * EclipseStore GigaMap JVector
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

/**
 * Entity of the migration fixtures. Persisted by name: the same class exists in the jvector module's test tree.
 */
public class MigrationDoc
{
    public final int     no;
    public       float[] vector;

    public MigrationDoc(final int no)
    {
        this.no     = no;
        this.vector = MigrationFixtures.position(no);
    }
}
