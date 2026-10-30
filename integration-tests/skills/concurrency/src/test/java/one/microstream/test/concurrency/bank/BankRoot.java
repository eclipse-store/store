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

import java.util.HashMap;
import java.util.Map;

public class BankRoot
{
    private final Map<String, Account> accounts = new HashMap<>();

    public Map<String, Account> accounts() { return accounts; }
}
