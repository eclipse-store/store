package test.microstream.cross.db;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - cross
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


import test.microstream.cross.entity.Customer;

import java.util.ArrayList;
import java.util.List;

public class DataRoot {

    private List<Customer> customers = new ArrayList<>();

    public List<Customer> getCustomers() {
        return this.customers;
    }
}
