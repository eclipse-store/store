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

public class Camp extends Accommodation
{
    private boolean hasElectricity;
    private int pitchCount;

    public Camp(
        UUID id, String name, String country, String city,
        double latitude, double longitude,
        double pricePerNight, String description,
        Set<String> amenities, float[] featureVector,
        boolean hasElectricity, int pitchCount)
    {
        super(id, name, country, city, latitude, longitude,
            pricePerNight, description, amenities, featureVector);
        this.hasElectricity = hasElectricity;
        this.pitchCount     = pitchCount;
    }

    public boolean hasElectricity()           { return hasElectricity; }
    public int pitchCount()                   { return pitchCount; }
    public void setHasElectricity(boolean b)  { this.hasElectricity = b; }
    public void setPitchCount(int c)          { this.pitchCount = c; }

    @Override
    public Type type() { return Type.CAMP; }
}
