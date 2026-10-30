package one.microstream.test.rootandobjectgraph;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - root-and-object-graph
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

/**
 * Custom root class — Pattern A from the {@code root-and-object-graph} skill.
 *
 * <p>{@code customersById} and {@code orders} are {@code final} so Eclipse Store
 * populates the existing collection instances in place on load. {@code settings}
 * is non-final because it is replaced atomically via {@link #setSettings(AppSettings)},
 * not mutated.
 */
public class AppRoot
{
    private final HashMap<String, Customer> customersById = new HashMap<>();
    private final ArrayList<Order>          orders        = new ArrayList<>();
    private       AppSettings               settings      = AppSettings.defaults();

    public HashMap<String, Customer> customers() { return this.customersById; }
    public ArrayList<Order>          orders()    { return this.orders; }
    public AppSettings               settings()  { return this.settings; }

    public void setSettings(final AppSettings s) { this.settings = s; }
}
