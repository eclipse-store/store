package one.microstream.test.afs;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - storage-targets-afs
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

import java.util.ArrayList;

public class Notebook
{
    private final ArrayList<Note> notes = new ArrayList<>();

    public ArrayList<Note> notes()
    {
        return this.notes;
    }
}
