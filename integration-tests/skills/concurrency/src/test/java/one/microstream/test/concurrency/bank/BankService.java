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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.serializer.concurrency.LockedExecutor;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

/**
 * Concurrent banking service backed by Eclipse Store.
 *
 * Locking strategy: {@link LockedExecutor} (RW). Chosen per SKILL.md
 * "Strategies, simple -> advanced": default for least boilerplate with
 * RW semantics. Every method that mutates Account state AND calls
 * {@code storage.store(...)} runs inside {@code exec.write(...)} so the
 * mutation + store pair is a single critical section (SKILL.md
 * "Mental model -- the single invariant").
 */
public class BankService implements AutoCloseable
{
    private final EmbeddedStorageManager storage;
    private final BankRoot               root;
    private final LockedExecutor         exec = LockedExecutor.New();

    public BankService(Path storageDir)
    {
        this.storage = EmbeddedStorage.start(new BankRoot(), storageDir);
        this.root    = (BankRoot) storage.root();
    }

    /** Seed N accounts each with the same opening balance. Idempotent across restarts. */
    public void ensureAccounts(int count, BigDecimal openingBalance)
    {
        exec.write(() -> {
            if (!root.accounts().isEmpty()) return;
            for (int i = 1; i <= count; i++)
            {
                String id = String.format("acc-%03d", i);
                root.accounts().put(id, new Account(id, openingBalance));
            }
            storage.store(root.accounts());
        });
    }

    public void transfer(String fromId, String toId, BigDecimal amount)
    {
        exec.write(() -> {
            if (fromId.equals(toId)) return;
            Account from = root.accounts().get(fromId);
            Account to   = root.accounts().get(toId);
            if (from == null || to == null) return;
            if (from.balance().compareTo(amount) < 0) return; // insufficient funds — skip

            from.setBalance(from.balance().subtract(amount));
            to.setBalance(to.balance().add(amount));
            // Single store() call covers both objects atomically for durability.
            // Two separate store() calls would risk a half-applied transfer
            // surviving a crash between them.
            storage.storeAll(from, to);
        });
    }

    public BigDecimal balance(String id)
    {
        return exec.read(() -> {
            Account a = root.accounts().get(id);
            return a == null ? BigDecimal.ZERO : a.balance();
        });
    }

    public BigDecimal totalBalance()
    {
        return exec.read(() -> {
            // snapshot inside the read lock; sum on the snapshot so we cannot
            // observe a half-finished transfer. SKILL.md Pitfall #3 (don't
            // return the mutable collection — sum and return a scalar).
            BigDecimal sum = BigDecimal.ZERO;
            for (Account a : root.accounts().values())
            {
                sum = sum.add(a.balance());
            }
            return sum;
        });
    }

    public List<String> accountIds()
    {
        return exec.read(() -> new ArrayList<>(root.accounts().keySet()));
    }

    @Override
    public void close()
    {
        storage.close();
    }
}
