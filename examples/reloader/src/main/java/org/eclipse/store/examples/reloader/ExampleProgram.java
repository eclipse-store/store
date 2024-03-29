package org.eclipse.store.examples.reloader;

/*-
 * #%L
 * EclipseStore Example Reloader
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.util.stream.Collectors;

import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceManager;
import org.eclipse.serializer.persistence.util.Reloader;
import org.eclipse.store.storage.types.StorageManager;

public class ExampleProgram
{

    public static void main(final String[] args)
    {
        final DataRoot root = new DataRoot();
        try (StorageManager storageManager = StorageProvider.createStorageManager("target/data", root)
                .start())
        {
            root.addItem("value 1");
            root.addItem("value 2");

            storageManager.store(root.getData());

            System.out.printf("Number of items in list: %s%n", root.getData()
                    .size());

            root.getData()
                    .clear();
            System.out.printf("Number of items in list after clear: %s (surprise :) )%n", root.getData()
                    .size());

            reload(storageManager.persistenceManager(), root);
            System.out.printf("Number of items in list after reload: %s%n", root.getData()
                    .size());

            System.out.println(root.getData()
                                       .stream()
                                       .collect(Collectors.joining(", ", "[", "]")));
        }
    }

    private static void reload(final PersistenceManager<Binary> persistenceManager, final DataRoot root)
    {
        final Reloader reloader = Reloader.New(persistenceManager);
        reloader.reloadFlat(root.getData());
        // reloadFlat is enough here as we just changed the List object.
        // You can also use .reloadFlat() to reload the object and all objects within the object if you have changed data
        // at multiple levels.
    }
}

