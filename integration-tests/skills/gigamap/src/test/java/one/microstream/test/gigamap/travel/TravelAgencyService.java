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

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.eclipse.store.gigamap.jvector.VectorIndex;
import org.eclipse.store.gigamap.jvector.VectorIndexConfiguration;
import org.eclipse.store.gigamap.jvector.VectorIndices;
import org.eclipse.store.gigamap.jvector.VectorSearchResult;
import org.eclipse.store.gigamap.lucene.LuceneContext;
import org.eclipse.store.gigamap.lucene.LuceneIndex;
import org.eclipse.store.gigamap.lucene.LuceneSearchResult;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

public class TravelAgencyService implements AutoCloseable
{
    public static final String VECTOR_INDEX_NAME = "accommodation-features";

    private final EmbeddedStorageManager   storage;
    private final AppRoot                  root;
    private final LuceneIndex<Accommodation> luceneIndex;
    private final VectorIndex<Accommodation> vectorIndex;

    public TravelAgencyService(Path storageDir, Path luceneDir)
    {
        this.storage = EmbeddedStorage.start(new AppRoot(), storageDir);
        this.root    = (AppRoot) storage.root();

        // Lucene — restart-safe: register() returns null if already attached,
        // so look it up via get() first.
        LuceneIndex<Accommodation> existingLucene =
            root.accommodations().index().get(LuceneIndex.class);
        if (existingLucene == null)
        {
            LuceneContext<Accommodation> ctx = LuceneContext.New(
                luceneDir, new AccommodationDocumentPopulator());
            this.luceneIndex = root.accommodations().index()
                .register(LuceneIndex.Category(ctx));
        }
        else
        {
            this.luceneIndex = existingLucene;
        }

        // jvector — same pattern. register(VectorIndices.Category()) returns null
        // on the second startup; fall back to get().
        VectorIndices<Accommodation> vectorIndices =
            root.accommodations().index().get(VectorIndices.class);
        if (vectorIndices == null)
        {
            vectorIndices = root.accommodations().index()
                .register(VectorIndices.Category());
        }

        VectorIndexConfiguration cfg = VectorIndexConfiguration
            .forSmallDataset(FeatureVectors.DIMENSION);

        this.vectorIndex = vectorIndices.ensure(
            VECTOR_INDEX_NAME, cfg, new AccommodationVectorizer());
    }

    public AppRoot root() { return root; }

    // -------------------------------------------------------------- write API

    public void addAccommodation(Accommodation a)
    {
        root.accommodations().add(a);
        root.accommodations().store();
    }

    public Reservation book(UUID accommodationId, String guestName,
        LocalDate checkIn, LocalDate checkOut)
    {
        Accommodation a = root.accommodations()
            .query(AccommodationIndices.id.is(accommodationId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown accommodation: " + accommodationId));

        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights <= 0) throw new IllegalArgumentException("checkOut must be after checkIn");

        double total = nights * a.pricePerNight();
        Reservation r = new Reservation(UUID.randomUUID(), accommodationId,
            guestName, checkIn, checkOut, total, Reservation.Status.CONFIRMED);

        root.reservations().add(r);
        root.reservations().store();
        return r;
    }

    public void cancel(UUID reservationId)
    {
        Reservation r = root.reservations()
            .query(ReservationIndices.id.is(reservationId))
            .findFirst()
            .orElseThrow();
        root.reservations().update(r, x -> x.setStatus(Reservation.Status.CANCELLED));
        root.reservations().store();
    }

    // -------------------------------------------------------------- read API

    public List<Accommodation> byCountry(String country)
    {
        return root.accommodations()
            .query(AccommodationIndices.country.is(country))
            .toList();
    }

    public List<Accommodation> byType(Accommodation.Type type)
    {
        return root.accommodations()
            .query(AccommodationIndices.type.is(type.name()))
            .toList();
    }

    public List<Accommodation> priceRange(double min, double max)
    {
        return root.accommodations()
            .query(AccommodationIndices.pricePerNight.between(min, max))
            .toList();
    }

    public List<Accommodation> withAllAmenities(String... amenities)
    {
        return root.accommodations()
            .query(AccommodationIndices.amenities.all(amenities))
            .toList();
    }

    public List<Accommodation> hotelsWithMinStars(int minStars)
    {
        return root.accommodations()
            .query(AccommodationIndices.type.is(Accommodation.Type.HOTEL.name())
                .and(AccommodationIndices.starRating.greaterThanEqual(minStars)))
            .toList();
    }

    /** Bounding-box pre-filter, exact haversine post-filter (skill Pattern: SpatialIndexer). */
    public List<Accommodation> nearby(double lat, double lon, double radiusKm)
    {
        var exact = AccommodationIndices.location.withinRadius(lat, lon, radiusKm);
        return root.accommodations()
            .query(AccommodationIndices.location.near(lat, lon, radiusKm))
            .stream()
            .filter(exact)
            .toList();
    }

    /** Lucene full-text search; standard Lucene query syntax. */
    public List<Accommodation> textSearch(String luceneQuery, int limit)
    {
        return luceneIndex.query(luceneQuery, limit);
    }

    /** Similarity search — k nearest by feature vector. */
    public VectorSearchResult<Accommodation> similar(Accommodation reference, int k)
    {
        return vectorIndex.search(reference, k);
    }

    /** Hybrid: full-text → restricted by similarity (Pattern H). */
    public List<Accommodation> textAndSimilar(
        String luceneQuery, Accommodation reference, int k, int luceneLimit)
    {
        LuceneSearchResult<Accommodation> textHits  = luceneIndex.search(luceneQuery, luceneLimit);
        VectorSearchResult<Accommodation> vectorHits = vectorIndex.search(reference, k);
        // Intersection by id-set; both sub-queries combine into a giga query
        return root.accommodations().query()
            .and(textHits)
            .and(vectorHits)
            .toList();
    }

    public List<Reservation> reservationsFor(UUID accommodationId)
    {
        return root.reservations()
            .query(ReservationIndices.accommodationId.is(accommodationId))
            .toList();
    }

    public List<Reservation> overlapping(LocalDate from, LocalDate to)
    {
        return root.reservations()
            .query(ReservationIndices.checkIn.before(to)
                .and(ReservationIndices.checkOut.after(from))
                .and(ReservationIndices.status.is(Reservation.Status.CONFIRMED.name())))
            .toList();
    }

    @Override
    public void close()
    {
        // Lucene holds an on-disk write.lock that storage.close() does NOT
        // release; close it explicitly so the same JVM can reopen the index.
        try { luceneIndex.close(); } catch (Exception ignore) {}
        storage.close();
    }
}
