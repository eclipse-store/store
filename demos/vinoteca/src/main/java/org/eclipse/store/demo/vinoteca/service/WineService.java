package org.eclipse.store.demo.vinoteca.service;

/*-
 * #%L
 * EclipseStore Demo Vinoteca
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

import org.eclipse.store.demo.vinoteca.dto.*;
import org.eclipse.store.demo.vinoteca.index.WineIndices;
import org.eclipse.store.demo.vinoteca.model.*;
import org.eclipse.store.gigamap.jvector.VectorIndex;
import org.eclipse.store.gigamap.jvector.VectorIndices;
import org.eclipse.store.gigamap.jvector.VectorSearchResult;
import org.eclipse.store.gigamap.lucene.LuceneIndex;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.GigaQuery;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Mutex;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Read;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Write;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Application service exposing all wine-related operations against the wines
 * {@link GigaMap} stored in the {@link DataRoot}.
 * <p>
 * The service combines plain CRUD with index-backed queries that exercise every flavour of
 * GigaMap index registered on the wines collection:
 * <ul>
 *   <li>bitmap indices (e.g. {@link #byType byType}, {@link #byCountry byCountry},
 *       {@link #byGrape byGrape}, {@link #byWinery byWinery});</li>
 *   <li>the Lucene full-text index (see {@link #fulltextSearch fulltextSearch});</li>
 *   <li>the JVector vector similarity index (see {@link #similar similar} and
 *       {@link #similarTo similarTo}).</li>
 * </ul>
 * The {@link Mutex @Mutex("wineStore")} annotation together with {@link Read @Read} and
 * {@link Write @Write} schedules concurrent invocations so that reads can run in parallel but
 * writes are exclusive — this matches EclipseStore's threading guarantees for the underlying
 * GigaMap.
 */
@Service
@Mutex("wineStore")
public class WineService
{
	private final DataRoot                    dataRoot;
	private final GigaMap<Wine>               wineGigaMap;
	private final GigaMap<Winery>             wineryGigaMap;
	private final LuceneIndex<Wine>           luceneIndex;
	private final Optional<VectorIndex<Wine>> vectorIndex;
	private final EmbeddedStorageManager      storageManager;

	/**
	 * Constructs the service and resolves the registered Lucene and (optional) vector indices
	 * from the wines GigaMap.
	 *
	 * @param dataRoot       the persistent root (provided by {@code DataRootConfig})
	 * @param storageManager the EclipseStore storage manager, used to {@code store} sub-graphs
	 *                       (such as a wine's review list) after mutations
	 */
	@SuppressWarnings("unchecked")
	public WineService(
		final DataRoot               dataRoot,
		final EmbeddedStorageManager storageManager
	)
	{
		this.dataRoot       = dataRoot;
		this.wineGigaMap    = dataRoot.getWines();
		this.wineryGigaMap  = dataRoot.getWineries();
		this.luceneIndex    = this.wineGigaMap.index().get(LuceneIndex.class);
		this.storageManager = storageManager;

		final VectorIndices<Wine> vectorIndices = this.wineGigaMap.index().get(VectorIndices.class);
		this.vectorIndex = Optional.ofNullable(vectorIndices)
			.map(vi -> vi.get("wine-embeddings"));
	}

	/**
	 * Returns a name-sorted page of wines.
	 *
	 * @param page the zero-based page number
	 * @param size the page size
	 * @return a page wrapping the wines in the requested slice and the total wine count
	 */
	@Read
	public PageResult<Wine> list(final int page, final int size)
	{
		final long total = this.wineGigaMap.size();
		final List<Wine> wines = this.wineGigaMap.query()
			.stream()
			.sorted(Comparator.comparing(Wine::getName))
			.skip((long) page * size)
			.limit(size)
			.toList();
		return new PageResult<>(wines, total, page, size);
	}

	/**
	 * Looks up a single wine by its GigaMap entity id.
	 *
	 * @param id the GigaMap entity id
	 * @return the wine, or {@code null} if no wine with that id exists
	 */
	@Read
	public Wine findById(final long id)
	{
		return this.wineGigaMap.get(id);
	}

	/**
	 * Creates a new wine, links it back to its producing winery, and persists both.
	 *
	 * @param input the wine to create
	 * @return the created wine
	 * @throws IllegalArgumentException if {@link WineInput#wineryId()} does not resolve to a winery
	 */
	@Write
	public Wine create(final WineInput input)
	{
		final Winery winery = this.wineryGigaMap.get(input.wineryId());
		if (winery == null)
		{
			throw new IllegalArgumentException("Winery not found: " + input.wineryId());
		}

		final Wine wine = new Wine(
			input.name(),
			winery,
			input.grapeVariety(),
			input.type(),
			input.vintage(),
			input.price(),
			0,
			0,
			input.tastingNotes(),
			input.aroma(),
			input.foodPairing(),
			input.alcoholContent(),
			input.bottlesInStock(),
			input.available()
		);

		this.wineGigaMap.add(wine);
		winery.getWines().add(wine);
		this.wineGigaMap.store();
		this.storageManager.store(winery.getWines());
		return wine;
	}

	/**
	 * Partially updates an existing wine. Only fields whose value in {@code input} is non-null
	 * (or, for primitives, greater than zero) are applied — this keeps the same DTO usable for
	 * full and partial updates. The mutation is performed via {@link GigaMap#update}, which
	 * guarantees that all registered indices stay in sync.
	 *
	 * @param id    the GigaMap entity id of the wine to update
	 * @param input the (partial) new field values
	 * @return the updated wine, or {@code null} if no wine with that id exists
	 */
	@Write
	public Wine update(final long id, final WineInput input)
	{
		final Wine wine = this.wineGigaMap.get(id);
		if (wine == null)
		{
			return null;
		}

		// Use gigaMap.update() to ensure indices are kept in sync
		this.wineGigaMap.update(wine, w -> {
			if (input.wineryId() > 0)
			{
				final Winery winery = this.wineryGigaMap.get(input.wineryId());
				if (winery != null)
				{
					w.setWinery(winery);
				}
			}
			if (input.name() != null)          w.setName(input.name());
			if (input.grapeVariety() != null)  w.setGrapeVariety(input.grapeVariety());
			if (input.type() != null)          w.setType(input.type());
			if (input.vintage() > 0)           w.setVintage(input.vintage());
			if (input.price() > 0)
			{
				w.setPrice(input.price());
			}
			if (input.tastingNotes() != null)  w.setTastingNotes(input.tastingNotes());
			if (input.aroma() != null)         w.setAroma(input.aroma());
			if (input.foodPairing() != null)   w.setFoodPairing(input.foodPairing());
			if (input.alcoholContent() > 0)    w.setAlcoholContent(input.alcoholContent());
			w.setBottlesInStock(input.bottlesInStock());
			w.setAvailable(input.available());
		});

		this.wineGigaMap.store();
		return wine;
	}

	/**
	 * Deletes a wine and removes it from the producing winery's wine list.
	 *
	 * @param id the GigaMap entity id of the wine to delete
	 * @return {@code true} if a wine was deleted, {@code false} if no wine had this id
	 */
	@Write
	public boolean delete(final long id)
	{
		final Wine wine = this.wineGigaMap.get(id);
		if (wine == null)
		{
			return false;
		}
		this.wineGigaMap.removeById(id);
		if (wine.getWinery() != null)
		{
			wine.getWinery().getWines().remove(wine);
			this.storageManager.store(wine.getWinery().getWines());
		}
		this.wineGigaMap.store();
		return true;
	}

	/**
	 * Returns all wines of the given type, served by the {@link WineIndices#TYPE} bitmap index.
	 *
	 * @param type the wine type
	 * @return name-sorted matching wines
	 */
	@Read
	public List<Wine> byType(final WineType type)
	{
		return this.wineGigaMap.query(WineIndices.TYPE.is(type))
			.stream()
			.sorted(Comparator.comparing(Wine::getName))
			.toList();
	}

	/**
	 * Returns all wines from the given country, served by the {@link WineIndices#COUNTRY} bitmap
	 * index (which de-references the producing winery).
	 *
	 * @param country the country to filter by (exact, case-sensitive match)
	 * @return name-sorted matching wines
	 */
	@Read
	public List<Wine> byCountry(final String country)
	{
		return this.wineGigaMap.query(WineIndices.COUNTRY.is(country))
			.stream()
			.sorted(Comparator.comparing(Wine::getName))
			.toList();
	}

	/**
	 * Returns all wines from the given region, served by the {@link WineIndices#REGION} bitmap
	 * index.
	 *
	 * @param region the region to filter by
	 * @return name-sorted matching wines
	 */
	@Read
	public List<Wine> byRegion(final String region)
	{
		return this.wineGigaMap.query(WineIndices.REGION.is(region))
			.stream()
			.sorted(Comparator.comparing(Wine::getName))
			.toList();
	}

	/**
	 * Returns all wines made from the given grape variety, served by the
	 * {@link WineIndices#GRAPE_VARIETY} bitmap index.
	 *
	 * @param grape the grape variety enum
	 * @return name-sorted matching wines
	 */
	@Read
	public List<Wine> byGrape(final GrapeVariety grape)
	{
		return this.wineGigaMap.query(WineIndices.GRAPE_VARIETY.is(grape))
			.stream()
			.sorted(Comparator.comparing(Wine::getName))
			.toList();
	}

	/**
	 * Returns all wines produced by the given winery, served by the
	 * {@link WineIndices#WINERY_NAME} bitmap index.
	 *
	 * @param winery the winery name (exact match)
	 * @return name-sorted matching wines
	 */
	@Read
	public List<Wine> byWinery(final String winery)
	{
		return this.wineGigaMap.query(WineIndices.WINERY_NAME.is(winery))
			.stream()
			.sorted(Comparator.comparing(Wine::getName))
			.toList();
	}

	/**
	 * Filters wines by combining the bitmap indices for type, grape variety, vintage, country and
	 * region into a single {@link GigaQuery}. Each non-{@code null} / non-blank parameter adds
	 * an AND condition to the query; if all parameters are empty, every wine is returned. The
	 * name parameter uses the name bitmap index with a case-insensitive contains predicate.
	 *
	 * @param name    substring to match against the wine name (case-insensitive), or {@code null}/blank to skip
	 * @param type    the wine type, or {@code null} to skip
	 * @param grape   the grape variety, or {@code null} to skip
	 * @param vintage the vintage year, or {@code null} to skip
	 * @param country the country, or {@code null}/blank to skip
	 * @param region  the region, or {@code null}/blank to skip
	 * @return name-sorted matching wines
	 */
	@Read
	public List<Wine> filter(final String name, final WineType type, final GrapeVariety grape, final Integer vintage, final String country, final String region)
	{
		final GigaQuery<Wine> query = this.wineGigaMap.query();
		if (name != null && !name.isBlank())
		{
			query.and(WineIndices.NAME.containsIgnoreCase(name));
		}
		if (type != null)
		{
			query.and(WineIndices.TYPE.is(type));
		}
		if (grape != null)
		{
			query.and(WineIndices.GRAPE_VARIETY.is(grape));
		}
		if (vintage != null)
		{
			query.and(WineIndices.VINTAGE.is(vintage));
		}
		if (country != null && !country.isBlank())
		{
			query.and(WineIndices.COUNTRY.is(country));
		}
		if (region != null && !region.isBlank())
		{
			query.and(WineIndices.REGION.is(region));
		}
		final List<Wine> result = query.toList();
		result.sort(Comparator.comparing(Wine::getName));
		return result;
	}

	/**
	 * Returns the distinct set of vintage years across all wines, taken directly from the bitmap
	 * index keys (no full scan).
	 *
	 * @return the distinct vintage list
	 */
	@Read
	public List<Integer> vintages()
	{
		return WineIndices.VINTAGE.resolveKeys(this.wineGigaMap);
	}

	/**
	 * Returns the distinct set of countries across all wines, taken directly from the bitmap
	 * index keys (no full scan).
	 *
	 * @return the distinct country list
	 */
	@Read
	public List<String> countries()
	{
		return WineIndices.COUNTRY.resolveKeys(this.wineGigaMap);
	}

	/**
	 * Returns the distinct set of regions across all wines, taken directly from the bitmap
	 * index keys (no full scan).
	 *
	 * @return the distinct region list
	 */
	@Read
	public List<String> regions()
	{
		return WineIndices.REGION.resolveKeys(this.wineGigaMap);
	}

	/**
	 * Returns all wines of a given vintage, served by the {@link WineIndices#VINTAGE} bitmap index.
	 *
	 * @param year the vintage year
	 * @return name-sorted matching wines
	 */
	@Read
	public List<Wine> byVintage(final int year)
	{
		return this.wineGigaMap.query(WineIndices.VINTAGE.is(year))
			.stream()
			.sorted(Comparator.comparing(Wine::getName))
			.toList();
	}

	/**
	 * Returns the top-rated wines, descending by {@link Wine#getRating()}.
	 *
	 * @param limit the maximum number of wines to return
	 * @return up to {@code limit} highest-rated wines
	 */
	@Read
	public List<Wine> topRated(final int limit)
	{
		return this.wineGigaMap.query()
			.stream()
			.sorted(Comparator.comparingDouble(Wine::getRating).reversed())
			.limit(limit)
			.toList();
	}

	/**
	 * Returns all wines whose price falls inside the given closed interval, sorted ascending by
	 * price. Performed as a full scan + filter.
	 *
	 * @param minPrice the inclusive lower bound on the price
	 * @param maxPrice the inclusive upper bound on the price
	 * @return matching wines, ascending by price
	 */
	@Read
	public List<Wine> priceRange(final double minPrice, final double maxPrice)
	{
		return this.wineGigaMap.query()
			.stream()
			.filter(w -> w.getPrice() >= minPrice && w.getPrice() <= maxPrice)
			.sorted(Comparator.comparingDouble(Wine::getPrice))
			.toList();
	}

	/**
	 * Runs a Lucene full-text query over the wine name, tasting notes, aroma and food-pairing
	 * fields populated by {@link org.eclipse.store.demo.vinoteca.index.WineDocumentPopulator
	 * WineDocumentPopulator}.
	 *
	 * @param query      the Lucene query string
	 * @param maxResults the maximum number of hits to return
	 * @return the matching wines, in Lucene relevance order
	 */
	@Read
	public List<Wine> fulltextSearch(final String query, final int maxResults)
	{
		return this.luceneIndex.query(query, maxResults);
	}

	/**
	 * Vector similarity search: encodes the {@code query} string with the same embedding model
	 * used to populate the wine vector index and returns the {@code k} nearest neighbours.
	 * <p>
	 * The query string is wrapped in a throw-away {@link Wine} purely to feed the existing
	 * {@link org.eclipse.store.demo.vinoteca.index.WineVectorizer WineVectorizer} (which expects a
	 * {@code Wine}). Returns an empty list if no vector index is configured (e.g. if Ollama is
	 * unreachable on startup).
	 *
	 * @param query the natural-language query
	 * @param k     the number of nearest neighbours to return
	 * @return up to {@code k} similar wines with their cosine similarity scores
	 */
	@Read
	public List<SimilarWineResult> similar(final String query, final int k)
	{
		return this.vectorIndex
			.map(index -> {
				final float[] queryVector = index.vectorizer().vectorize(
					new Wine(query, null, null, null, 0, 0, 0, 0, query, null, null, 0, 0, false)
				);
				final VectorSearchResult<Wine> results = index.search(queryVector, k);
				return results.stream()
					.map(entry -> new SimilarWineResult(entry.entity(), entry.score()))
					.toList();
			})
			.orElse(List.of());
	}

	/**
	 * Vector similarity search anchored on an existing wine — finds the {@code k} closest wines
	 * to {@code wineId}. The anchor wine itself is filtered out of the result.
	 *
	 * @param wineId the GigaMap entity id of the anchor wine
	 * @param k      the number of similar wines to return
	 * @return up to {@code k} similar wines (excluding the anchor) with their cosine similarity
	 *         scores; an empty list if {@code wineId} is unknown or no vector index is configured
	 */
	@Read
	public List<SimilarWineResult> similarTo(final long wineId, final int k)
	{
		final Wine wine = this.wineGigaMap.get(wineId);
		if (wine == null)
		{
			return List.of();
		}
		return this.vectorIndex
			.map(index -> {
				final float[] vector = index.vectorizer().vectorize(wine);
				final VectorSearchResult<Wine> results = index.search(vector, k + 1);
				return results.stream()
					.filter(entry -> entry.entity() != wine)
					.limit(k)
					.map(entry -> new SimilarWineResult(entry.entity(), entry.score()))
					.toList();
			})
			.orElse(List.of());
	}

	/**
	 * Loads the reviews of a wine, triggering the lazy reference if necessary.
	 *
	 * @param wineId the GigaMap entity id of the wine
	 * @return the (possibly empty) list of reviews; an empty list if {@code wineId} is unknown
	 */
	@Read
	public List<Review> getReviews(final long wineId)
	{
		final Wine wine = this.wineGigaMap.get(wineId);
		return wine != null && wine.getReviews() != null ? wine.getReviews() : List.of();
	}

	/**
	 * Loads the reviews of a wine, triggering the lazy reference if necessary.
	 *
	 * @param wine the wine; may be {@code null}
	 * @return the (possibly empty) list of reviews; an empty list if {@code wine} is {@code null}
	 */
	@Read
	public List<Review> getReviews(final Wine wine)
	{
		return wine != null && wine.getReviews() != null ? wine.getReviews() : List.of();
	}

	/**
	 * Adds a review to a wine identified by its entity id and recomputes the wine's average
	 * {@link Wine#getRating() rating} and {@link Wine#getRatingCount() rating count}.
	 *
	 * @param wineId the GigaMap entity id of the wine
	 * @param input  the review payload
	 * @return the updated wine
	 * @throws IllegalArgumentException if {@code wineId} does not resolve to a wine
	 */
	@Write
	public Wine addReview(final long wineId, final ReviewInput input)
	{
		final Wine wine = this.wineGigaMap.get(wineId);
		if (wine == null)
		{
			throw new IllegalArgumentException("Wine not found: " + wineId);
		}
		return this.addReview(wine, input);
	}

	/**
	 * Adds a review to the given wine and recomputes the wine's average
	 * {@link Wine#getRating() rating} and {@link Wine#getRatingCount() rating count}. The mutation
	 * is performed inside {@link GigaMap#update} so the bitmap, Lucene and vector indices
	 * registered on the wines GigaMap are kept consistent.
	 *
	 * @param wine  the wine to attach the review to (must not be {@code null})
	 * @param input the review payload
	 * @return the updated wine
	 * @throws IllegalArgumentException if {@code wine} is {@code null}
	 */
	@Write
	public Wine addReview(final Wine wine, final ReviewInput input)
	{
		if (wine == null)
		{
			throw new IllegalArgumentException("Wine must not be null");
		}
		final Customer customer = this.dataRoot.getCustomers().get(input.customerIndex());
		final Review review = new Review(customer, input.rating(), input.text(), LocalDateTime.now());

		this.wineGigaMap.update(wine, w -> {
			w.getReviews().add(review);
			final double newAvg = w.getReviews().stream()
				.mapToDouble(Review::getRating)
				.average()
				.orElse(0);
			w.setRating(Math.round(newAvg * 10.0) / 10.0);
			w.setRatingCount(w.getReviews().size());
		});

		this.wineGigaMap.store();
		this.storageManager.store(wine.getReviews());
		return wine;
	}

	/**
	 * Computes aggregate catalog statistics for the analytics view: total count, average rating,
	 * average price, distribution by {@link org.eclipse.store.demo.vinoteca.model.WineType WineType}
	 * and distribution by country. All averages are rounded to one decimal place.
	 *
	 * @return the aggregate statistics, or a zero-result if the catalog is empty
	 */
	@Read
	public WineStatsResult getStats()
	{
		final long total = this.wineGigaMap.size();
		if (total == 0)
		{
			return new WineStatsResult(0, 0, 0, Map.of(), Map.of());
		}

		final double avgRating = this.wineGigaMap.query().stream()
			.mapToDouble(Wine::getRating).average().orElse(0);
		final double avgPrice = this.wineGigaMap.query().stream()
			.mapToDouble(Wine::getPrice).average().orElse(0);
		final Map<String, Long> typeDist = this.wineGigaMap.query().stream()
			.collect(Collectors.groupingBy(w -> w.getType().name(), Collectors.counting()));
		final Map<String, Long> countryDist = this.wineGigaMap.query().stream()
			.collect(Collectors.groupingBy(w -> w.getWinery().getCountry(), Collectors.counting()));

		return new WineStatsResult(
			total,
			Math.round(avgRating * 10.0) / 10.0,
			Math.round(avgPrice * 100.0) / 100.0,
			typeDist,
			countryDist
		);
	}
}
