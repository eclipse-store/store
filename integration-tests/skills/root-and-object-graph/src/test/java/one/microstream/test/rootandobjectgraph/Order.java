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

public final class Order
{
    private final String id;
    private final String customerId;
    private final long   amountCents;

    public Order(final String id, final String customerId, final long amountCents)
    {
        this.id          = id;
        this.customerId  = customerId;
        this.amountCents = amountCents;
    }

    public String id()          { return this.id; }
    public String customerId()  { return this.customerId; }
    public long   amountCents() { return this.amountCents; }
}
