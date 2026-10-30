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

public final class FeatureVectors
{
    public static final int DIMENSION = 8;

    public static float[] build(
        Accommodation.Type type,
        double pricePerNight,
        int starRating,
        Set<String> amenities)
    {
        float[] v = new float[DIMENSION];
        v[0] = type == Accommodation.Type.HOTEL     ? 1f : 0f;
        v[1] = type == Accommodation.Type.CAMP      ? 1f : 0f;
        v[2] = type == Accommodation.Type.APARTMENT ? 1f : 0f;
        v[3] = (float) Math.min(1.0, pricePerNight / 500.0);
        v[4] = amenities.contains("pool")    ? 1f : 0f;
        v[5] = amenities.contains("wifi")    ? 1f : 0f;
        v[6] = amenities.contains("parking") ? 1f : 0f;
        v[7] = starRating / 5f;
        return normalize(v);
    }

    private static float[] normalize(float[] v)
    {
        double sq = 0;
        for (float f : v) sq += f * f;
        double norm = Math.sqrt(sq);
        if (norm == 0) return v;
        for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
        return v;
    }

    private FeatureVectors() {}
}
