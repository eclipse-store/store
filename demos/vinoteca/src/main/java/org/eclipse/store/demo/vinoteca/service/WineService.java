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
import org.eclipse.store.gigamap.jvector.VectorSearchResult;
import org.eclipse.store.gigamap.lucene.LuceneIndex;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Mutex;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Read;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Write;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

	public WineService(
		final DataRoot                 dataRoot,
		final EmbeddedStorageManager   storageManager,
		@Autowired(required = false) final VectorIndex<Wine> vectorIndex
	)
	{
		this.dataRoot       = dataRoot;
		this.wineGigaMap    = dataRoot.getWines();
		this.wineryGigaMap  = dataRoot.getWineries();
		this.luceneIndex    = this.wineGigaMap.index().get(LuceneIndex.class);
		this.storageManager = storageManager;
		this.vectorIndex    = Optional.ofNullable(vectorIndex);
	}

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

	@Read
	public Wine findById(final long id)
	{
		return this.wineGigaMap.get(id);
	}

	@Write
	public Wine create(final WineInput input)
	{
		final Winery winery = this.wineryGigaMap.get(input.wineryId());
		if (winery == null)
		{
			throw new IllegalArgumentException("Winery not found: " + input.wineryId());
		}

		final MonetaryAmount price = Monetary.getDefaultAmountFactory()
			.setCurrency(input.currency() != null ? input.currency() : "EUR")
			.setNumber(input.price())
			.create();

		final Wine wine = new Wine(
			input.name(),
			winery,
			input.grapeVariety(),
			input.type(),
			input.vintage(),
			price,
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
				w.setPrice(Monetary.getDefaultAmountFactory()
					.setCurrency(input.currency() != null ? input.currency() : "EUR")
					.setNumber(input.price())
					.create());
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

	@Read
	public List<Wine> byType(final String type)
	{
		return this.wineGigaMap.query(WineIndices.TYPE.is(type.toUpperCase()))
			.stream()
			.sorted(Comparator.comparing(Wine::getName))
			.toList();
	}

	@Read
	public List<Wine> byCountry(final String country)
	{
		return this.wineGigaMap.query(WineIndices.COUNTRY.is(country))
			.stream()
			.sorted(Comparator.comparing(Wine::getName))
			.toList();
	}

	@Read
	public List<Wine> byRegion(final String region)
	{
		return this.wineGigaMap.query(WineIndices.REGION.is(region))
			.stream()
			.sorted(Comparator.comparing(Wine::getName))
			.toList();
	}

	@Read
	public List<Wine> byGrape(final String grape)
	{
		return this.wineGigaMap.query(WineIndices.GRAPE_VARIETY.is(grape.toUpperCase()))
			.stream()
			.sorted(Comparator.comparing(Wine::getName))
			.toList();
	}

	@Read
	public List<Wine> byWinery(final String winery)
	{
		return this.wineGigaMap.query(WineIndices.WINERY_NAME.is(winery))
			.stream()
			.sorted(Comparator.comparing(Wine::getName))
			.toList();
	}

	@Read
	public List<Wine> byVintage(final int year)
	{
		return this.wineGigaMap.query()
			.stream()
			.filter(w -> w.getVintage() == year)
			.sorted(Comparator.comparing(Wine::getName))
			.toList();
	}

	@Read
	public List<Wine> topRated(final int limit)
	{
		return this.wineGigaMap.query()
			.stream()
			.sorted(Comparator.comparingDouble(Wine::getRating).reversed())
			.limit(limit)
			.toList();
	}

	@Read
	public List<Wine> priceRange(final double minPrice, final double maxPrice)
	{
		return this.wineGigaMap.query()
			.stream()
			.filter(w -> w.getPriceAsDouble() >= minPrice && w.getPriceAsDouble() <= maxPrice)
			.sorted(Comparator.comparingDouble(Wine::getPriceAsDouble))
			.toList();
	}

	@Read
	public List<Wine> fulltextSearch(final String query, final int maxResults)
	{
		return this.luceneIndex.query(query, maxResults);
	}

	@Read
	public List<SimilarWineResult> similar(final String query, final int k)
	{
		return this.vectorIndex
			.map(index -> {
				final float[] queryVector = index.vectorizer().vectorize(
					new Wine(query, null, null, null, 0, null, 0, 0, query, null, null, 0, 0, false)
				);
				final VectorSearchResult<Wine> results = index.search(queryVector, k);
				return results.stream()
					.map(entry -> new SimilarWineResult(entry.entity(), entry.score()))
					.toList();
			})
			.orElse(List.of());
	}

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

	@Read
	public List<Review> getReviews(final long wineId)
	{
		final Wine wine = this.wineGigaMap.get(wineId);
		return wine != null && wine.getReviews() != null ? wine.getReviews() : List.of();
	}

	@Read
	public List<Review> getReviews(final Wine wine)
	{
		return wine != null && wine.getReviews() != null ? wine.getReviews() : List.of();
	}

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
			.mapToDouble(Wine::getPriceAsDouble).average().orElse(0);
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
