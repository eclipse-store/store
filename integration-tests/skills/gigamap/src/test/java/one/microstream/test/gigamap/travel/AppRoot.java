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

import org.eclipse.store.gigamap.types.GigaMap;

public class AppRoot
{
    private final GigaMap<Accommodation> accommodations = GigaMap.<Accommodation>Builder()
        .withBitmapIdentityIndex(AccommodationIndices.id)
        .withBitmapIndex(AccommodationIndices.type)
        .withBitmapIndex(AccommodationIndices.country)
        .withBitmapIndex(AccommodationIndices.city)
        .withBitmapIndex(AccommodationIndices.pricePerNight)
        .withBitmapIndex(AccommodationIndices.starRating)
        .withBitmapIndex(AccommodationIndices.amenities)
        .withBitmapIndex(AccommodationIndices.location)
        .build();

    private final GigaMap<Reservation> reservations = GigaMap.<Reservation>Builder()
        .withBitmapIdentityIndex(ReservationIndices.id)
        .withBitmapIndex(ReservationIndices.accommodationId)
        .withBitmapIndex(ReservationIndices.guestName)
        .withBitmapIndex(ReservationIndices.checkIn)
        .withBitmapIndex(ReservationIndices.checkOut)
        .withBitmapIndex(ReservationIndices.status)
        .build();

    public GigaMap<Accommodation> accommodations() { return accommodations; }
    public GigaMap<Reservation>   reservations()   { return reservations; }
}
