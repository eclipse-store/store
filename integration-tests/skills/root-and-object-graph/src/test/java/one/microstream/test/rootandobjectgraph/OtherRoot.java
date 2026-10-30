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
 * A different root class used by the Pattern C (root migration) test.
 * Same shape conceptually, but a distinct type — verifies that
 * {@code setRoot(newRoot) + storeRoot()} can switch the root class.
 */
public class OtherRoot
{
    private final HashMap<String, Customer> customersByName = new HashMap<>();
    private final ArrayList<String>         notes           = new ArrayList<>();
    private       String                    label           = "migrated";

    public HashMap<String, Customer> customers() { return this.customersByName; }
    public ArrayList<String>         notes()     { return this.notes; }
    public String                    label()     { return this.label; }

    public void setLabel(final String l) { this.label = l; }
}
