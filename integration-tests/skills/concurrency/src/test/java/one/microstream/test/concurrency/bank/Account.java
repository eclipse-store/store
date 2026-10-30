package one.microstream.test.concurrency.bank;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - concurrency
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

import java.math.BigDecimal;

public class Account
{
    private final String id;
    private BigDecimal balance;

    public Account(String id, BigDecimal balance)
    {
        this.id = id;
        this.balance = balance;
    }

    public String id() { return id; }
    public BigDecimal balance() { return balance; }

    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
