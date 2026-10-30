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

import java.util.Set;
import java.util.UUID;

public class Apartment extends Accommodation
{
    private int bedrooms;
    private int maxGuests;

    public Apartment(
        UUID id, String name, String country, String city,
        double latitude, double longitude,
        double pricePerNight, String description,
        Set<String> amenities, float[] featureVector,
        int bedrooms, int maxGuests)
    {
        super(id, name, country, city, latitude, longitude,
            pricePerNight, description, amenities, featureVector);
        this.bedrooms  = bedrooms;
        this.maxGuests = maxGuests;
    }

    public int bedrooms()              { return bedrooms; }
    public int maxGuests()             { return maxGuests; }
    public void setBedrooms(int b)     { this.bedrooms = b; }
    public void setMaxGuests(int g)    { this.maxGuests = g; }

    @Override
    public Type type() { return Type.APARTMENT; }
}
