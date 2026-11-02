package test.eclipse.store.persister;

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


import org.eclipse.serializer.persistence.types.Persister;

public class MyEntity
{

    String name;
    int value;
    transient Persister storage;

    public MyEntity(String name, int value)
    {
        this.name = name;
        this.value = value;
    }

    public Persister getStorage()
    {
        return storage;
    }

    public void setStorage(Persister storage)
    {
        this.storage = storage;
    }
}
