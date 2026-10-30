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

public class Hotel extends Accommodation
{
    private int starRating;

    public Hotel(
        UUID id, String name, String country, String city,
        double latitude, double longitude,
        double pricePerNight, String description,
        Set<String> amenities, float[] featureVector,
        int starRating)
    {
        super(id, name, country, city, latitude, longitude,
            pricePerNight, description, amenities, featureVector);
        this.starRating = starRating;
    }

    public int starRating()                  { return starRating; }
    public void setStarRating(int s)         { this.starRating = s; }

    @Override
    public Type type() { return Type.HOTEL; }
}
