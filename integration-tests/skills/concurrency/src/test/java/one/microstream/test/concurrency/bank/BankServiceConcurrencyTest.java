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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BankServiceConcurrencyTest
{
    private static final int        ACCOUNT_COUNT     = 100;
    private static final BigDecimal OPENING_BALANCE   = new BigDecimal("1000.00");
    private static final BigDecimal EXPECTED_TOTAL    =
        OPENING_BALANCE.multiply(BigDecimal.valueOf(ACCOUNT_COUNT));
    private static final int        WRITER_COUNT      = 8;
    private static final int        READER_COUNT      = 4;
    // Missing/wrong locking surfaces within milliseconds at this thread count, so a short window
    // is enough to catch it: ~500 ms JIT warmup + ~500 ms of saturated stress still yields tens of
    // thousands of invariant checks across the reader threads. Kept short to keep the JDK-matrix CI
    // fast; bump this locally if you want more statistical confidence.
    private static final long       DURATION_NANOS    = TimeUnit.SECONDS.toNanos(1);

    @Test
    void total_balance_invariant_holds_under_load_and_across_restart(@TempDir Path tmp)
        throws IOException, InterruptedException
    {
        Path storageDir = tmp.resolve("storage");
        Files.createDirectories(storageDir);

        AtomicLong transfersAttempted = new AtomicLong();
        AtomicLong transfersExecuted  = new AtomicLong();
        AtomicLong reads              = new AtomicLong();
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        // ---- session 1: stress ----------------------------------------------
        try (BankService svc = new BankService(storageDir))
        {
            svc.ensureAccounts(ACCOUNT_COUNT, OPENING_BALANCE);
            assertEquals(0, EXPECTED_TOTAL.compareTo(svc.totalBalance()),
                "seed invariant");

            List<String> ids = svc.accountIds();
            assertEquals(ACCOUNT_COUNT, ids.size());

            long deadline = System.nanoTime() + DURATION_NANOS;
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> threads = new ArrayList<>();

            for (int i = 0; i < WRITER_COUNT; i++)
            {
                Thread t = new Thread(() -> {
                    awaitQuietly(start);
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    try
                    {
                        while (System.nanoTime() < deadline)
                        {
                            String from = ids.get(rnd.nextInt(ACCOUNT_COUNT));
                            String to   = ids.get(rnd.nextInt(ACCOUNT_COUNT));
                            if (from.equals(to)) continue;
                            BigDecimal amount = BigDecimal.valueOf(rnd.nextInt(1, 51));
                            transfersAttempted.incrementAndGet();
                            BigDecimal pre = svc.balance(from);
                            svc.transfer(from, to, amount);
                            // count actually-executed transfers loosely: if pre>=amount
                            // it had a chance of executing; this is just diagnostic.
                            if (pre.compareTo(amount) >= 0) transfersExecuted.incrementAndGet();
                        }
                    }
                    catch (Throwable err) { firstError.compareAndSet(null, err); }
                }, "writer-" + i);
                threads.add(t);
                t.start();
            }

            for (int i = 0; i < READER_COUNT; i++)
            {
                Thread t = new Thread(() -> {
                    awaitQuietly(start);
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    try
                    {
                        while (System.nanoTime() < deadline)
                        {
                            BigDecimal total = svc.totalBalance();
                            assertNotNull(total);
                            // invariant must hold continuously
                            assertEquals(0, EXPECTED_TOTAL.compareTo(total),
                                "in-flight total drifted: " + total);
                            String id = "acc-" + String.format("%03d", rnd.nextInt(1, ACCOUNT_COUNT + 1));
                            BigDecimal b = svc.balance(id);
                            assertNotNull(b);
                            assertTrue(b.signum() >= 0, "negative balance for " + id + ": " + b);
                            reads.incrementAndGet();
                        }
                    }
                    catch (Throwable err) { firstError.compareAndSet(null, err); }
                }, "reader-" + i);
                threads.add(t);
                t.start();
            }

            start.countDown();
            for (Thread t : threads) t.join();

            if (firstError.get() != null)
            {
                throw new AssertionError("worker thread failed", firstError.get());
            }

            BigDecimal live = svc.totalBalance();
            assertEquals(0, EXPECTED_TOTAL.compareTo(live),
                "live in-memory total after stress: " + live);

            System.out.printf(
                "stress done: writers=%d readers=%d duration=%ds attempted=%d executed=%d reads=%d%n",
                WRITER_COUNT, READER_COUNT,
                TimeUnit.NANOSECONDS.toSeconds(DURATION_NANOS),
                transfersAttempted.get(), transfersExecuted.get(), reads.get());
        }

        // ---- session 2: reopen, verify durability ---------------------------
        try (BankService svc = new BankService(storageDir))
        {
            BigDecimal restored = svc.totalBalance();
            assertEquals(0, EXPECTED_TOTAL.compareTo(restored),
                "persisted total after restart: " + restored);
            assertEquals(ACCOUNT_COUNT, svc.accountIds().size());
        }
    }

    private static void awaitQuietly(CountDownLatch latch)
    {
        try { latch.await(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
