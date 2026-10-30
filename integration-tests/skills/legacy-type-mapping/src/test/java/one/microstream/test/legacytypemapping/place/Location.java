package one.microstream.test.legacytypemapping.place;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - legacy-type-mapping
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

public class Location
{
    String directions;
    double latitude;
    double longitude;

    public Location()
    {
        // default
    }

    public Location(final String directions, final double latitude, final double longitude)
    {
        this.directions = directions;
        this.latitude   = latitude;
        this.longitude  = longitude;
    }

    public String directions()
    {
        return this.directions;
    }

    public double latitude()
    {
        return this.latitude;
    }

    public double longitude()
    {
        return this.longitude;
    }
}
