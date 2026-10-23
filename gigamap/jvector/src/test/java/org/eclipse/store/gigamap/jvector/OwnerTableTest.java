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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link OwnerTable}: slot leases, which return a slot only when every holder is done, and
 * the leave logs whose captures become a segment's deletions.
 */
class OwnerTableTest
{
    @Test
    void allocate_takesTheLowestFreeSlot()
    {
        final OwnerTable table = OwnerTable.New();

        assertEquals(0, table.allocate().slot());
        assertEquals(1, table.allocate().slot());
        assertEquals(2, table.slotsInUse());
    }

    @Test
    void allocate_allSlotsTaken_returnsNull()
    {
        final OwnerTable table = OwnerTable.New();
        for(int slot = 0; slot <= SegmentOwners.MAX_SLOT; slot++)
        {
            table.allocate();
        }

        assertNull(table.allocate());
    }

    @Test
    void allocate_takenSlot_throws()
    {
        final OwnerTable table = OwnerTable.New();
        table.allocate(3);

        assertThrows(IllegalStateException.class, () -> table.allocate(3));
    }

    /**
     * A frozen graph and the segment written from it share a lease: the slot is free only once both
     * are closed, so nothing can make it the owner of an entity while either may still be searched.
     */
    @Test
    void release_sharedLease_freesTheSlotOnlyAfterTheLastHolder()
    {
        final OwnerTable       table = OwnerTable.New();
        final OwnerTable.Lease lease = table.allocate();
        lease.retain();

        lease.release();
        assertEquals(1, table.slotsInUse());
        assertNotEquals(lease.slot(), table.allocate().slot(), "a held slot must not be handed out again");

        lease.release();
        assertEquals(1, table.slotsInUse());
        assertEquals(lease.slot(), table.allocate().slot());
    }

    @Test
    void release_moreOftenThanHeld_throws()
    {
        final OwnerTable.Lease lease = OwnerTable.New().allocate();
        lease.release();

        assertThrows(IllegalStateException.class, lease::release);
        assertThrows(IllegalStateException.class, lease::retain);
    }

    @Test
    void set_leaveFromASegmentSlot_isLogged()
    {
        final OwnerTable table   = OwnerTable.New();
        final int        segment = table.allocate().slot();
        final int        live    = table.allocate().slot();
        table.liveSlot(live);
        table.owners().assign(10, segment);
        table.owners().assign(11, segment);

        table.set(10, live);
        table.set(11, SegmentOwners.NONE);

        assertEquals(live, table.owner(10));
        assertEquals(SegmentOwners.NONE, table.owner(11));
        assertArrayEquals(new int[]{10, 11}, table.capture(segment).sortedFrom(0));
    }

    /**
     * The live graph's deletions at a freeze are its deleted nodes, which the flush purges, and an
     * entity can leave and re-enter it, so its leaves are not logged at all.
     */
    @Test
    void set_leaveFromTheLiveSlot_isNotLogged()
    {
        final OwnerTable table = OwnerTable.New();
        final int        live  = table.allocate().slot();
        table.liveSlot(live);

        table.set(10, live);
        table.set(10, SegmentOwners.NONE);
        table.set(10, live);

        assertEquals(0, table.capture(live).length());
    }

    @Test
    void set_unchangedOwner_isNotLogged()
    {
        final OwnerTable table   = OwnerTable.New();
        final int        segment = table.allocate().slot();
        table.owners().assign(10, segment);

        table.set(10, segment);

        assertEquals(0, table.capture(segment).length());
    }

    /**
     * A capture is what the manifest of one flush records. Leaves logged after it, even ones that
     * grow the log's array, must not change it.
     */
    @Test
    void capture_isUnaffectedByLaterLeaves()
    {
        final OwnerTable table   = OwnerTable.New();
        final int        segment = table.allocate().slot();
        final int        live    = table.allocate().slot();
        table.liveSlot(live);
        for(int id = 0; id < 100; id++)
        {
            table.owners().assign(id, segment);
        }
        table.set(3, live);
        table.set(1, live);

        final OwnerTable.LeaveLog captured = table.capture(segment);
        for(int id = 4; id < 100; id++)
        {
            table.set(id, live);
        }

        assertArrayEquals(new int[]{1, 3}, captured.sortedFrom(0));
        assertArrayEquals(new int[]{1}, captured.sortedFrom(1), "entries after the first, in log order 3, 1");
        assertEquals(98, table.capture(segment).length());
    }

    @Test
    void allocate_reusedSlot_startsWithAnEmptyLog()
    {
        final OwnerTable       table = OwnerTable.New();
        final OwnerTable.Lease first = table.allocate();
        final int              live  = table.allocate().slot();
        table.liveSlot(live);
        table.owners().assign(7, first.slot());
        table.set(7, live);
        first.release();

        final OwnerTable.Lease reused = table.allocate();

        assertEquals(first.slot(), reused.slot());
        assertEquals(0, table.capture(reused.slot()).length());
    }

}
