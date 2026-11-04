package test.eclipse.store.test.gigamap.travel;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.eclipse.store.gigamap.jvector.VectorSearchResult;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TravelAgencyServiceTest
{
    @Test
    void bitmap_lucene_and_vector_indices_work_end_to_end(@TempDir Path tmp) throws IOException
    {
        Path storageDir = tmp.resolve("storage");
        Path luceneDir  = tmp.resolve("lucene");
        Files.createDirectories(storageDir);
        Files.createDirectories(luceneDir);

        UUID hotelId;
        UUID reservationId;

        // ---- first session: seed + query ------------------------------------
        try (TravelAgencyService svc = new TravelAgencyService(storageDir, luceneDir))
        {
            SampleData.seed().forEach(svc::addAccommodation);

            List<Accommodation> czech = svc.byCountry("CZ");
            assertEquals(5, czech.size(), "expected 5 CZ accommodations");

            // Pagination: two pages of 3 and the rest, no overlap, nothing lost
            List<Accommodation> page1 = svc.byCountry("CZ", 0, 3);
            List<Accommodation> page2 = svc.byCountry("CZ", 3, 3);
            assertEquals(3, page1.size());
            assertEquals(2, page2.size());
            Set<UUID> paged = new HashSet<>();
            page1.forEach(a -> paged.add(a.id()));
            page2.forEach(a -> paged.add(a.id()));
            assertEquals(5, paged.size(), "pages must not overlap");

            List<Accommodation> hotels = svc.byType(Accommodation.Type.HOTEL);
            assertEquals(4, hotels.size());

            List<Accommodation> cheap = svc.priceRange(0, 1000);
            assertTrue(cheap.stream().allMatch(a -> a.pricePerNight() <= 1000));
            assertFalse(cheap.isEmpty());

            List<Accommodation> withPool = svc.withAllAmenities("wifi", "pool");
            assertTrue(withPool.stream().allMatch(a ->
                a.amenities().contains("wifi") && a.amenities().contains("pool")));

            // Spatial: near Prague — 50 km radius
            List<Accommodation> nearPrague = svc.nearby(50.0875, 14.4214, 50);
            assertFalse(nearPrague.isEmpty(), "expected something within 50 km of Prague");
            assertTrue(nearPrague.stream().anyMatch(a -> a.city().equals("Praha")));

            // Lucene full-text
            List<Accommodation> textHits = svc.textSearch("description:wellness", 10);
            assertFalse(textHits.isEmpty(), "Lucene should find 'wellness' text");

            List<Accommodation> riverHits = svc.textSearch("description:Danube", 10);
            assertEquals(3, riverHits.size(), "expected three Danube descriptions");

            // Vector similarity — pick a hotel, find similar
            Accommodation grand = hotels.stream()
                .filter(h -> h.name().equals("Grand Praha"))
                .findFirst().orElseThrow();
            hotelId = grand.id();

            VectorSearchResult<Accommodation> similar = svc.similar(grand, 5);
            List<Accommodation> similarList = similar.stream()
                .map(e -> e.entity())
                .toList();
            assertFalse(similarList.isEmpty());
            // Self-vector → top hit should be itself
            assertEquals(grand.id(), similarList.get(0).id());
            // Next hits should be other hotels (same type one-hot dim is the biggest)
            assertTrue(similarList.stream().skip(1).limit(3)
                .allMatch(a -> a.type() == Accommodation.Type.HOTEL),
                "expected hotels to dominate the similarity ranking");

            // Book it
            Reservation r = svc.book(hotelId, "Guest",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));
            reservationId = r.id();
            assertEquals(4 * grand.pricePerNight(), r.totalPrice());

            List<Reservation> forHotel = svc.reservationsFor(hotelId);
            assertEquals(1, forHotel.size());
        }

        // ---- second session: reload, verify durability + idempotent ensure --
        // The storage shutdown of the first session closed the Lucene and vector
        // indices; reopening in the same JVM must not hit Lucene's write.lock.
        try (TravelAgencyService svc = new TravelAgencyService(storageDir, luceneDir))
        {
            assertEquals(10, svc.root().accommodations().size());
            assertEquals(1,  svc.root().reservations().size());

            // Lucene results survive (the index at the named location reopens)
            List<Accommodation> hits = svc.textSearch("description:Danube", 10);
            assertEquals(3, hits.size());

            // Vector index also reloads — similar() still ranks the same hotel first
            Accommodation grand = svc.root().accommodations()
                .query(AccommodationIndices.id.is(hotelId)).findFirst().orElseThrow();
            VectorSearchResult<Accommodation> similar = svc.similar(grand, 3);
            assertEquals(grand.id(), similar.stream().findFirst().orElseThrow().entity().id());

            // Cancel
            svc.cancel(reservationId);
            Reservation cancelled = svc.root().reservations()
                .query(ReservationIndices.id.is(reservationId)).findFirst().orElseThrow();
            assertEquals(Reservation.Status.CANCELLED, cancelled.status());
        }
    }

    @Test
    void hybrid_lucene_plus_vector_subquery(@TempDir Path tmp) throws IOException
    {
        Path storageDir = tmp.resolve("storage");
        Path luceneDir  = tmp.resolve("lucene");
        Files.createDirectories(storageDir);
        Files.createDirectories(luceneDir);

        try (TravelAgencyService svc = new TravelAgencyService(storageDir, luceneDir))
        {
            SampleData.seed().forEach(svc::addAccommodation);

            Accommodation grand = svc.root().accommodations()
                .query(AccommodationIndices.country.is("CZ")
                    .and(AccommodationIndices.type.is(Accommodation.Type.HOTEL.name())))
                .stream()
                .max(Comparator.comparingDouble(Accommodation::pricePerNight))
                .orElseThrow();

            // Hilton Budapest has "pool and spa" in description and is the closest
            // hotel by feature-vector to Grand Praha → expect it in the hybrid result.
            List<Accommodation> hybrid = svc.textAndSimilar(
                "description:pool", grand, 10, 50);
            assertNotNull(hybrid);
            assertTrue(hybrid.stream().anyMatch(a -> a.name().equals("Hilton Budapest")),
                "expected Hilton Budapest in pool hybrid result, got: " + hybrid);
        }
    }

    @Test
    void update_via_map_keeps_indices_consistent(@TempDir Path tmp) throws IOException
    {
        Path storageDir = tmp.resolve("storage");
        Path luceneDir  = tmp.resolve("lucene");
        Files.createDirectories(storageDir);
        Files.createDirectories(luceneDir);

        try (TravelAgencyService svc = new TravelAgencyService(storageDir, luceneDir))
        {
            Accommodation a = SampleData.seed().get(0);
            svc.addAccommodation(a);

            UUID id = a.id();
            assertEquals(1, svc.byCountry(a.country()).size());

            svc.root().accommodations().update(a, x -> x.setCountry("DE"));
            svc.root().accommodations().store();

            assertEquals(0, svc.byCountry("CZ").size(),
                "the moved entity should be gone from the CZ index");
            assertEquals(1, svc.byCountry("DE").size());
            assertNotEquals(0, id.variant());
        }
    }

    /** An index registered after the data exists is back-filled (skill Pattern B / Example 6). */
    private static final IndexerString<Accommodation> nameIndex = new IndexerString.Abstract<>()
    {
        @Override public String name() { return "accommodation.name"; }
        @Override protected String getString(Accommodation a) { return a.name(); }
    };

    @Test
    void index_registered_on_a_populated_map_is_backfilled_and_reindex_repairs_a_direct_mutation(@TempDir Path tmp)
        throws IOException
    {
        Path storageDir = tmp.resolve("storage");
        Path luceneDir  = tmp.resolve("lucene");
        Files.createDirectories(storageDir);
        Files.createDirectories(luceneDir);

        try (TravelAgencyService svc = new TravelAgencyService(storageDir, luceneDir))
        {
            SampleData.seed().forEach(svc::addAccommodation);

            // ensure() on a populated map back-fills the new index from every entity
            svc.root().accommodations().index().bitmap().ensure(nameIndex);
            svc.root().accommodations().store();

            List<Accommodation> grand = svc.root().accommodations()
                .query(nameIndex.startsWith("Grand")).toList();
            assertEquals(1, grand.size());
            assertEquals("Grand Praha", grand.get(0).name());

            // A direct mutation bypasses the indices: the old key still answers ...
            Accommodation a = grand.get(0);
            a.setCity("Brno");                                   // WRONG in application code
            svc.storage().store(a);                              // GigaMap did not track it
            assertEquals(2, svc.root().accommodations()
                .query(AccommodationIndices.city.is("Praha")).count(), "stale index still lists Praha");

            // ... until reindex() rebuilds every index from the current entity state
            svc.root().accommodations().reindex();
            svc.root().accommodations().store();
            assertEquals(1, svc.root().accommodations()
                .query(AccommodationIndices.city.is("Praha")).count());
            assertEquals(1, svc.root().accommodations()
                .query(AccommodationIndices.city.is("Brno")).count());
        }

        // The later-registered index and the rebuilt ones survive a restart; ensure() is a no-op now
        try (TravelAgencyService svc = new TravelAgencyService(storageDir, luceneDir))
        {
            svc.root().accommodations().index().bitmap().ensure(nameIndex);
            assertEquals(1, svc.root().accommodations().query(nameIndex.startsWith("Grand")).count());
            assertEquals(1, svc.root().accommodations()
                .query(AccommodationIndices.city.is("Brno")).count());
        }
    }
}
