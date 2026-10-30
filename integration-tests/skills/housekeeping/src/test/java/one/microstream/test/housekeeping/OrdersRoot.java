package one.microstream.test.housekeeping;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - housekeeping
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
import java.util.List;

public class OrdersRoot
{
    private final List<Order> orders = new ArrayList<>();

    public List<Order> orders() { return this.orders; }
}
