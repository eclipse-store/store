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

import java.util.ArrayList;
import java.util.HashMap;

public class AppRoot
{
    public AppRoot() {}

    private final HashMap<String, Customer> customersById = new HashMap<>();
    private final ArrayList<Order>          orders        = new ArrayList<>();

    public HashMap<String, Customer> customers() { return customersById; }
    public ArrayList<Order>          orders()    { return orders; }
}
