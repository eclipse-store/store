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

public class Reservation
{
    private final UUID id;
    private UUID accommodationId;
    private String guestName;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private double totalPrice;
    private Status status;

    public enum Status { PENDING, CONFIRMED, CANCELLED }

    public Reservation(UUID id, UUID accommodationId, String guestName,
        LocalDate checkIn, LocalDate checkOut, double totalPrice, Status status)
    {
        this.id              = id;
        this.accommodationId = accommodationId;
        this.guestName       = guestName;
        this.checkIn         = checkIn;
        this.checkOut        = checkOut;
        this.totalPrice      = totalPrice;
        this.status          = status;
    }

    public UUID      id()              { return id; }
    public UUID      accommodationId() { return accommodationId; }
    public String    guestName()       { return guestName; }
    public LocalDate checkIn()         { return checkIn; }
    public LocalDate checkOut()        { return checkOut; }
    public double    totalPrice()      { return totalPrice; }
    public Status    status()          { return status; }

    public void setStatus(Status s)            { this.status = s; }
    public void setCheckIn(LocalDate d)        { this.checkIn = d; }
    public void setCheckOut(LocalDate d)       { this.checkOut = d; }
    public void setTotalPrice(double p)        { this.totalPrice = p; }
    public void setGuestName(String n)         { this.guestName = n; }

    @Override
    public String toString()
    {
        return "Reservation[" + guestName + " @ " + accommodationId
            + " " + checkIn + "→" + checkOut + " " + status + "]";
    }
}
