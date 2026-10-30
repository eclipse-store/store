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

import java.util.UUID;

import org.eclipse.store.gigamap.types.BinaryIndexerUUID;
import org.eclipse.store.gigamap.types.IndexerDouble;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.gigamap.types.IndexerMultiValue;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.gigamap.types.SpatialIndexer;

public final class AccommodationIndices
{
    public static final BinaryIndexerUUID<Accommodation> id =
        new BinaryIndexerUUID.Abstract<>()
        {
            @Override
            protected UUID getUUID(Accommodation a) { return a.id(); }
        };

    public static final IndexerString<Accommodation> type =
        new IndexerString.Abstract<>()
        {
            @Override
            public String getString(Accommodation a) { return a.type().name(); }
        };

    public static final IndexerString<Accommodation> country =
        new IndexerString.Abstract<>()
        {
            @Override
            public String getString(Accommodation a) { return a.country(); }
        };

    public static final IndexerString<Accommodation> city =
        new IndexerString.Abstract<>()
        {
            @Override
            public String getString(Accommodation a) { return a.city(); }
        };

    public static final IndexerDouble<Accommodation> pricePerNight =
        new IndexerDouble.Abstract<>()
        {
            @Override
            protected Double getDouble(Accommodation a) { return a.pricePerNight(); }
        };

    public static final IndexerInteger<Accommodation> starRating =
        new IndexerInteger.Abstract<>()
        {
            @Override
            protected Integer getInteger(Accommodation a)
            {
                return a instanceof Hotel h ? h.starRating() : 0;
            }
        };

    public static final IndexerMultiValue<Accommodation, String> amenities =
        new IndexerMultiValue.Abstract<Accommodation, String>()
        {
            @Override
            public Iterable<String> indexEntityMultiValue(Accommodation a)
            {
                return a.amenities();
            }
            @Override
            public Class<String> keyType() { return String.class; }
        };

    public static final SpatialIndexer.Abstract<Accommodation> location =
        new SpatialIndexer.Abstract<Accommodation>()
        {
            @Override
            protected Double getLatitude(Accommodation a)  { return a.latitude(); }
            @Override
            protected Double getLongitude(Accommodation a) { return a.longitude(); }
        };

    private AccommodationIndices() {}
}
