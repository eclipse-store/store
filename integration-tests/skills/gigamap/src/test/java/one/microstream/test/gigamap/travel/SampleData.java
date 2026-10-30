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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SampleData
{
    public static List<Accommodation> seed()
    {
        return List.of(
            hotel("Grand Praha", "CZ", "Praha", 50.0875, 14.4214,
                3800, 5, "Luxusní hotel v centru Prahy, blízko Karlova mostu, wellness a bazén.",
                Set.of("wifi", "pool", "parking", "spa", "restaurant")),
            hotel("Aparthotel Vienna", "AT", "Vienna", 48.2082, 16.3738,
                2900, 4, "Modern hotel near the Vienna State Opera, free breakfast and wifi.",
                Set.of("wifi", "parking", "breakfast")),
            hotel("Hilton Budapest", "HU", "Budapest", 47.4979, 19.0402,
                4100, 5, "Five-star hotel above the Danube with pool and spa.",
                Set.of("wifi", "pool", "spa", "parking", "restaurant")),
            hotel("Penzion U Lva", "CZ", "Český Krumlov", 48.8127, 14.3175,
                1400, 3, "Útulný penzion v historickém centru, snídaně v ceně.",
                Set.of("wifi", "breakfast")),

            apartment("Vltava Apartment", "CZ", "Praha", 50.0808, 14.4144,
                2200, "Stylish 2-bedroom apartment with river view, fully equipped kitchen.",
                Set.of("wifi", "kitchen", "washing_machine"), 2, 4),
            apartment("Donau Suite", "AT", "Vienna", 48.2200, 16.3700,
                2600, "Spacious apartment near the Danube, perfect for families.",
                Set.of("wifi", "kitchen", "parking"), 3, 6),
            apartment("Tatra View", "SK", "Štrbské Pleso", 49.1186, 20.0617,
                1800, "Mountain apartment with stunning Tatra view, ski-in/ski-out.",
                Set.of("wifi", "kitchen", "ski_storage", "parking"), 2, 5),

            camp("Lipno Lake Camp", "CZ", "Lipno", 48.6354, 14.2317,
                550, "Family camping by Lipno lake, electric pitches and swimming.",
                Set.of("electricity", "wifi", "swimming", "parking"), true, 120),
            camp("Donau Camping", "AT", "Linz", 48.3069, 14.2858,
                620, "Riverside camping on the Danube, electricity at every pitch.",
                Set.of("electricity", "wifi", "playground"), true, 80),
            camp("Wild Camp Šumava", "CZ", "Modrava", 49.0211, 13.4936,
                250, "Rustic forest camping, no electricity, hiking trails nearby.",
                Set.of("hiking", "parking"), false, 30)
        );
    }

    private static Hotel hotel(String name, String country, String city,
        double lat, double lon, double price, int stars, String desc,
        Set<String> amenities)
    {
        return new Hotel(UUID.randomUUID(), name, country, city, lat, lon, price, desc,
            new LinkedHashSet<>(amenities),
            FeatureVectors.build(Accommodation.Type.HOTEL, price, stars, amenities),
            stars);
    }

    private static Apartment apartment(String name, String country, String city,
        double lat, double lon, double price, String desc,
        Set<String> amenities, int bedrooms, int maxGuests)
    {
        return new Apartment(UUID.randomUUID(), name, country, city, lat, lon, price, desc,
            new LinkedHashSet<>(amenities),
            FeatureVectors.build(Accommodation.Type.APARTMENT, price, 0, amenities),
            bedrooms, maxGuests);
    }

    private static Camp camp(String name, String country, String city,
        double lat, double lon, double price, String desc,
        Set<String> amenities, boolean electricity, int pitchCount)
    {
        return new Camp(UUID.randomUUID(), name, country, city, lat, lon, price, desc,
            new LinkedHashSet<>(amenities),
            FeatureVectors.build(Accommodation.Type.CAMP, price, 0, amenities),
            electricity, pitchCount);
    }

    private SampleData() {}
}
