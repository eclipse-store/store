package one.microstream.test.springboot;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - spring-boot
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

import org.eclipse.store.integrations.spring.boot.types.concurrent.Read;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Write;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.springframework.stereotype.Service;

@Service
public class CustomerService
{
    private final EmbeddedStorageManager storage;
    private final AppRoot                root;

    public CustomerService(EmbeddedStorageManager storage)
    {
        this.storage = storage;
        this.root    = storage.root();
    }

    @Write
    public void add(Customer c)
    {
        root.customers().put(c.email(), c);
        storage.store(root.customers());
    }

    @Read
    public Customer find(String email)
    {
        return root.customers().get(email);
    }
}
