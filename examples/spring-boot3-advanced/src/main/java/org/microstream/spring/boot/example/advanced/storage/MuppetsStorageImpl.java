package org.microstream.spring.boot.example.advanced.storage;

/*-
 * #%L
 * EclipseStore Example Spring Boot3 Advanced
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Read;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Write;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.microstream.spring.boot.example.advanced.model.MuppetsRoot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MuppetsStorageImpl implements MuppetStorage
{

    private final EmbeddedStorageManager storageManager;

    public MuppetsStorageImpl(
            @Qualifier("muppets")
            EmbeddedStorageManager storageManager
    )
    {
        this.storageManager = storageManager;
    }

    @Override
    @Read
    public String oneMuppet(Integer id)
    {
        MuppetsRoot root = (MuppetsRoot) storageManager.root();
        if (id > root.getMuppets().size())
        {
            throw new IllegalArgumentException("No muppet with this id");
        }
        return root.getMuppets().get(id);
    }

    @Override
    @Read
    public List<String> allMuppets()
    {
        MuppetsRoot root = (MuppetsRoot) storageManager.root();
        return new ArrayList<>(root.getMuppets()); // Create new List... never return original one.

    }

    @Override
    @Write
    public int addMuppets(List<String> muppets)
    {
        MuppetsRoot root = (MuppetsRoot) storageManager.root();
        root.setMuppets(muppets);
        Storer eagerStorer = storageManager.createEagerStorer();
        eagerStorer.store(root);
        eagerStorer.commit();
        return root.getMuppets().size();

    }
}
