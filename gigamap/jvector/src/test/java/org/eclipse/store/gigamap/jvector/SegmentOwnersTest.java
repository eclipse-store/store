package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
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


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SegmentOwners}, which decides the one source an entity is live in.
 */
class SegmentOwnersTest
{
    @Test
    void owner_newInstance_isNoneEverywhere()
    {
        final SegmentOwners owners = SegmentOwners.New();

        assertEquals(SegmentOwners.NONE, owners.owner(0));
        assertEquals(SegmentOwners.NONE, owners.owner(70_000));
        assertEquals(SegmentOwners.NONE, owners.owner(Integer.MAX_VALUE));
        assertEquals(SegmentOwners.NONE, owners.owner(-1));
    }

    @Test
    void assign_acrossPageBoundary_eachIdKeepsItsSlot()
    {
        final SegmentOwners owners = SegmentOwners.New();

        owners.assign(65_535, 0);
        owners.assign(65_536, 1);
        owners.assign(200_000, SegmentOwners.MAX_SLOT);

        assertEquals(0, owners.owner(65_535));
        assertEquals(1, owners.owner(65_536));
        assertEquals(SegmentOwners.MAX_SLOT, owners.owner(200_000));
        assertEquals(SegmentOwners.NONE, owners.owner(65_534), "a fresh page reads NONE, not slot 0");
        assertEquals(SegmentOwners.NONE, owners.owner(65_537));
    }

    @Test
    void assign_highestId_isAddressable()
    {
        final SegmentOwners owners = SegmentOwners.New();

        owners.assign(Integer.MAX_VALUE, 7);

        assertEquals(7, owners.owner(Integer.MAX_VALUE));
        assertEquals(SegmentOwners.NONE, owners.owner(Integer.MAX_VALUE - 1));
    }

    @Test
    void assign_outOfRange_throws()
    {
        final SegmentOwners owners = SegmentOwners.New();

        assertThrows(IllegalArgumentException.class, () -> owners.assign(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> owners.assign(0, -1));
        assertThrows(IllegalArgumentException.class, () -> owners.assign(0, SegmentOwners.NONE));
    }

    @Test
    void set_returnsThePreviousOwner()
    {
        final SegmentOwners owners = SegmentOwners.New();

        assertEquals(SegmentOwners.NONE, owners.set(5, 2));
        assertEquals(2, owners.set(5, 3));
        assertEquals(3, owners.set(5, SegmentOwners.NONE));
        assertEquals(SegmentOwners.NONE, owners.owner(5));
    }

    @Test
    void set_noneOnAnUnallocatedPage_returnsNoneAndAllocatesNothing()
    {
        final SegmentOwners owners = SegmentOwners.New();
        owners.assign(5, 2);

        assertEquals(SegmentOwners.NONE, owners.set(1_000_000, SegmentOwners.NONE));
        assertEquals(SegmentOwners.NONE, owners.owner(1_000_000));
        assertEquals(2, owners.owner(5));
    }

    @Test
    void set_outOfRange_throws()
    {
        final SegmentOwners owners = SegmentOwners.New();

        assertThrows(IllegalArgumentException.class, () -> owners.set(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> owners.set(0, -1));
        assertThrows(IllegalArgumentException.class, () -> owners.set(0, SegmentOwners.NONE + 1));
    }

    @Test
    void move_fromTheCurrentOwner_movesAndOtherwiseLeavesItAlone()
    {
        final SegmentOwners owners = SegmentOwners.New();
        owners.assign(5, 1);

        assertFalse(owners.move(5, 2, 3), "not owned by the expected slot");
        assertEquals(1, owners.owner(5));
        assertTrue(owners.move(5, 1, 3));
        assertEquals(3, owners.owner(5));
        assertFalse(owners.move(70_000, 1, 3), "an id without a page is owned by nobody");
        assertEquals(SegmentOwners.NONE, owners.owner(70_000));
        assertThrows(IllegalArgumentException.class, () -> owners.move(5, SegmentOwners.NONE, 1));
        assertThrows(IllegalArgumentException.class, () -> owners.move(5, 3, SegmentOwners.NONE));
    }

    /**
     * A merge moves owners without the monitor while the mutating writer sets them: for every entity
     * exactly one of the two wins, and the writer's set never gets lost behind a move.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void move_concurrentWithTheWriter_neverOverwritesTheWritersSet() throws Exception
    {
        final int           count  = 200_000;
        final SegmentOwners owners = SegmentOwners.New();
        for(int id = 0; id < count; id++)
        {
            owners.assign(id, 1);
        }
        final ExecutorService executor = Executors.newFixedThreadPool(2, runnable ->
        {
            final Thread thread = new Thread(runnable, "owners-test");
            thread.setDaemon(true);
            return thread;
        });
        final boolean[] moved = new boolean[count];
        final int[]     taken = new int[count];
        try
        {
            final CountDownLatch start  = new CountDownLatch(1);
            final Future<?>      merge  = executor.submit(() ->
            {
                start.await();
                for(int id = 0; id < count; id++)
                {
                    moved[id] = owners.move(id, 1, 2);
                }
                return null;
            });
            final Future<?>      writer = executor.submit(() ->
            {
                start.await();
                for(int id = 0; id < count; id += 3)
                {
                    taken[id] = owners.set(id, 7);
                }
                return null;
            });
            start.countDown();
            merge.get();
            writer.get();
        }
        finally
        {
            executor.shutdownNow();
        }

        for(int id = 0; id < count; id++)
        {
            if(id % 3 == 0)
            {
                assertEquals(7, owners.owner(id), "the writer's set stands, id " + id);
                assertEquals(moved[id] ? 2 : 1, taken[id], "the writer saw the move if it came first, id " + id);
            }
            else
            {
                assertTrue(moved[id]);
                assertEquals(2, owners.owner(id));
            }
        }
    }

    /**
     * One writer, as under the parent monitor, moves every entity from its first owner to a second
     * one or to nobody, growing the page directory as it goes; readers must only ever see the first
     * owner and then the final one, never anything else.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void set_singleWriterConcurrentWithReads_readersSeeTheOldOwnerThenTheNewOne() throws Exception
    {
        final int           count  = 200_000;
        final SegmentOwners owners = SegmentOwners.New();
        for(int id = 0; id < count / 2; id++)
        {
            owners.assign(id, id % 3);
        }

        final int             readers  = 4;
        // Daemon threads, so a reader a regression leaves spinning cannot keep the test JVM alive.
        final ExecutorService executor = Executors.newFixedThreadPool(readers + 1, runnable ->
        {
            final Thread thread = new Thread(runnable, "owners-test");
            thread.setDaemon(true);
            return thread;
        });
        try
        {
            final CountDownLatch  start   = new CountDownLatch(1);
            final List<Future<?>> futures = new ArrayList<>();
            futures.add(executor.submit(() ->
            {
                start.await();
                for(int id = 0; id < count; id++)
                {
                    // Ids of the second half land on pages that do not exist yet.
                    owners.set(id, finalOwner(id));
                }
                return null;
            }));
            for(int r = 0; r < readers; r++)
            {
                futures.add(executor.submit(() ->
                {
                    start.await();
                    for(int id = 0; id < count; id++)
                    {
                        final int initial = id < count / 2 ? id % 3 : SegmentOwners.NONE;
                        int owner;
                        while((owner = owners.owner(id)) != finalOwner(id))
                        {
                            if(owner != initial)
                            {
                                throw new AssertionError("id " + id + " read owner " + owner);
                            }
                            if(Thread.currentThread().isInterrupted())
                            {
                                throw new InterruptedException("id " + id + " never reached its final owner");
                            }
                            Thread.onSpinWait();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for(final Future<?> future : futures)
            {
                future.get();
            }
        }
        finally
        {
            executor.shutdownNow();
        }

        for(int id = 0; id < count; id++)
        {
            assertEquals(finalOwner(id), owners.owner(id), "id " + id);
        }
    }

    private static int finalOwner(final int id)
    {
        return id % 5 == 0 ? SegmentOwners.NONE : 3 + id % 7;
    }

}
