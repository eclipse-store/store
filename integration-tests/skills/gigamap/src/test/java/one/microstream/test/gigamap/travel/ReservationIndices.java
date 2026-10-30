package one.microstream.test.gigamap.travel;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - gigamap
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

import java.time.LocalDate;
import java.util.UUID;

import org.eclipse.store.gigamap.types.BinaryIndexerUUID;
import org.eclipse.store.gigamap.types.IndexerLocalDate;
import org.eclipse.store.gigamap.types.IndexerString;

public final class ReservationIndices
{
    public static final BinaryIndexerUUID<Reservation> id =
        new BinaryIndexerUUID.Abstract<>()
        {
            @Override
            protected UUID getUUID(Reservation r) { return r.id(); }
        };

    public static final BinaryIndexerUUID<Reservation> accommodationId =
        new BinaryIndexerUUID.Abstract<>()
        {
            @Override
            protected UUID getUUID(Reservation r) { return r.accommodationId(); }
        };

    public static final IndexerString<Reservation> guestName =
        new IndexerString.Abstract<>()
        {
            @Override
            public String getString(Reservation r) { return r.guestName(); }
        };

    public static final IndexerLocalDate<Reservation> checkIn =
        new IndexerLocalDate.Abstract<>()
        {
            @Override
            protected LocalDate getLocalDate(Reservation r) { return r.checkIn(); }
        };

    public static final IndexerLocalDate<Reservation> checkOut =
        new IndexerLocalDate.Abstract<>()
        {
            @Override
            protected LocalDate getLocalDate(Reservation r) { return r.checkOut(); }
        };

    public static final IndexerString<Reservation> status =
        new IndexerString.Abstract<>()
        {
            @Override
            public String getString(Reservation r) { return r.status().name(); }
        };

    private ReservationIndices() {}
}
