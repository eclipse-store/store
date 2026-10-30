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

import java.util.List;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

public class NotesService implements AutoCloseable
{
    private final EmbeddedStorageManager storage;
    private final NotesRoot              root;

    public NotesService(EmbeddedStorageManager storage)
    {
        this.storage = storage;
        Object existing = storage.root();
        if (existing instanceof NotesRoot)
        {
            this.root = (NotesRoot) existing;
        }
        else
        {
            this.root = new NotesRoot();
            storage.setRoot(this.root);
            storage.storeRoot();
        }
    }

    public void add(Note note)
    {
        root.notes().add(note);
    }

    public List<Note> all()
    {
        return root.notes();
    }

    public void store()
    {
        storage.store(root.notes());
    }

    @Override
    public void close()
    {
        storage.close();
    }
}
