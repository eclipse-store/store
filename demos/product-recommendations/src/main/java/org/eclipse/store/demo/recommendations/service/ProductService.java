package org.eclipse.store.demo.recommendations.service;

/*-
 * #%L
 * EclipseStore Demo Product Recommendations
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import org.eclipse.store.demo.recommendations.model.Product;
import org.eclipse.store.gigamap.jvector.VectorIndex;
import org.eclipse.store.gigamap.jvector.VectorSearchResult;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.ScoredSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Core service managing product storage, vector-based similarity search, and catalog lifecycle.
 *
 * <p>This service acts as the central orchestrator for the product recommendations demo,
 * bridging the {@link GigaMap} storage layer, the {@link VectorIndex} for similarity search,
 * and the {@link EmbeddingModel} for query vectorization. It is responsible for:
 * <ul>
 *   <li>Loading the product catalog from {@code products.json} at construction time</li>
 *   <li>Seeding the GigaMap with an initial batch of products on first startup</li>
 *   <li>Adding random products from the remaining catalog pool on demand</li>
 *   <li>Performing semantic similarity searches by embedding query strings and searching
 *       the vector index</li>
 *   <li>Providing paginated access to all stored products</li>
 * </ul>
 *
 * <h2>Initialization</h2>
 * <p>On startup, the service checks whether the GigaMap is already populated (e.g., from
 * a previous run with persistence enabled). If empty, it seeds the map with up to
 * {@value #INITIAL_PRODUCT_COUNT} randomly selected products from the catalog. Products
 * used for seeding are removed from the available pool so they won't be added again via
 * {@link #addRandomProducts(int)}.
 *
 * <h2>Search Flow</h2>
 * <p>When a search query is received:
 * <ol>
 *   <li>The query string is embedded into a 384-dimensional vector using the
 *       {@link EmbeddingModel}</li>
 *   <li>The vector is passed to the {@link VectorIndex} for approximate nearest-neighbor
 *       (ANN) search</li>
 *   <li>The top-{@code k} results are returned, each containing the matched {@link Product}
 *       and a similarity score (higher values indicate greater similarity)</li>
 * </ol>
 *
 * @see GigaMap
 * @see VectorIndex
 * @see ProductVectorizer
 * @see ProductController
 */
@Service
public class ProductService
{
	private static final Logger LOG                  = LoggerFactory.getLogger(ProductService.class);

	/**
	 * The number of products to seed into the GigaMap on first startup.
	 */
	private static final int    INITIAL_PRODUCT_COUNT = 500;

	private final GigaMap<Product>      gigaMap;
	private final VectorIndex<Product>  vectorIndex;
	private final EmbeddingModel        embeddingModel;
	private final List<Product>         availableProducts;
	private final Random                random = new Random();

	/**
	 * Creates a new {@code ProductService} with the given dependencies.
	 *
	 * <p>The product catalog is loaded from {@code products.json} on the classpath during
	 * construction. This mutable list serves as the pool of available products that can
	 * be added to the GigaMap via {@link #addRandomProducts(int)}.
	 *
	 * @param gigaMap        the GigaMap storing product entities
	 * @param vectorIndex    the vector index for similarity search over products
	 * @param embeddingModel the embedding model used to vectorize search queries
	 */
	public ProductService(
		final GigaMap<Product> gigaMap,
		final VectorIndex<Product> vectorIndex,
		final EmbeddingModel embeddingModel
	)
	{
		this.gigaMap        = gigaMap;
		this.vectorIndex    = vectorIndex;
		this.embeddingModel = embeddingModel;
		this.availableProducts = loadProductCatalog();
	}

	/**
	 * Initializes the GigaMap with an initial batch of products if it is currently empty.
	 *
	 * <p>This method is invoked automatically by Spring after dependency injection is complete.
	 * If the GigaMap already contains data (e.g., from a previous application run with
	 * persistence), initialization is skipped to avoid duplicate entries.
	 *
	 * <p>Up to {@value #INITIAL_PRODUCT_COUNT} products are randomly selected from the
	 * available catalog and added to the GigaMap, where they are automatically vectorized
	 * and indexed by the configured {@link VectorIndex}.
	 */
	@PostConstruct
	public void init()
	{
		if(!this.gigaMap.isEmpty())
		{
			LOG.info("GigaMap already contains {} products, skipping initialization.", this.gigaMap.size());
			return;
		}

		final int count = Math.min(INITIAL_PRODUCT_COUNT, this.availableProducts.size());
		LOG.info("Initializing GigaMap with {} products...", count);

		this.addRandomProducts(count);

		LOG.info("Initialization complete. GigaMap contains {} products.", this.gigaMap.size());
	}

	/**
	 * Adds a specified number of random products from the available catalog pool to the GigaMap.
	 *
	 * <p>Products are selected randomly and removed from the available pool to prevent
	 * duplicates. If the pool contains fewer products than requested, only the remaining
	 * products are added. The added products are automatically vectorized and indexed by
	 * the {@link VectorIndex}, making them immediately searchable.
	 *
	 * @param count the desired number of random products to add
	 * @return the list of products that were actually added (may be smaller than {@code count}
	 *         if the available pool is exhausted)
	 */
	public List<Product> addRandomProducts(final int count)
	{
		final List<Product> added = new ArrayList<>(count);
		for(int i = 0; i < count && !this.availableProducts.isEmpty(); i++)
		{
			added.add(this.availableProducts.remove(this.random.nextInt(this.availableProducts.size())));
		}
		this.gigaMap.addAll(added);
		return added;
	}

	/**
	 * Performs a semantic similarity search for products matching the given natural-language query.
	 *
	 * <p>The query string is first embedded into a dense vector using the configured
	 * {@link EmbeddingModel}. This query vector is then compared against all product vectors
	 * in the {@link VectorIndex} using the configured similarity function (cosine similarity),
	 * and the top-{@code k} most similar products are returned.
	 *
	 * <p>The similarity score in each {@link SearchResult} ranges from 0 to 1 for cosine
	 * similarity, where values closer to 1 indicate higher semantic similarity between the
	 * query and the product.
	 *
	 * @param query the natural-language search query (e.g., "running shoes", "kitchen gadgets")
	 * @param k     the maximum number of results to return
	 * @return a list of {@link SearchResult} entries ordered by descending similarity score
	 */
	public List<SearchResult> search(final String query, final int k)
	{
		final float[] queryVector = this.embeddingModel.embed(query).content().vector();
		final VectorSearchResult<Product> result = this.vectorIndex.search(queryVector, k);

		final List<SearchResult> results = new ArrayList<>();
		for(final ScoredSearchResult.Entry<Product> entry : result)
		{
			results.add(new SearchResult(entry.entity(), entry.score()));
		}
		return results;
	}

	/**
	 * Returns a paginated view of all products currently stored in the GigaMap.
	 *
	 * <p>Products are returned in the iteration order of the underlying GigaMap.
	 * Pagination is implemented via stream {@code skip}/{@code limit} operations.
	 *
	 * @param page the zero-based page index
	 * @param size the maximum number of products per page
	 * @return a {@link PageResult} containing the requested page of products along with
	 *         pagination metadata (total count, current page, page size)
	 */
	public PageResult list(final int page, final int size)
	{
		final long total = this.gigaMap.size();
		final int  skip  = page * size;

		final List<Product> products = this.gigaMap.query().stream().skip(skip).limit(size).toList();

		return new PageResult(products, total, page, size);
	}

	/**
	 * Loads the full product catalog from the {@code products.json} classpath resource.
	 *
	 * <p>The JSON file is expected to contain a top-level array of product objects, each
	 * with {@code name}, {@code category}, {@code price}, and {@code in_stock} fields.
	 * The returned list is mutable, allowing products to be removed as they are added
	 * to the GigaMap.
	 *
	 * @return a mutable list of all products in the catalog
	 * @throws RuntimeException if the file cannot be read or parsed
	 */
	private static List<Product> loadProductCatalog()
	{
		try(final InputStream is = new ClassPathResource("products.json").getInputStream())
		{
			return new ObjectMapper().readValue(is, new TypeReference<>() {});
		}
		catch(final IOException e)
		{
			throw new RuntimeException("Failed to load products.json", e);
		}
	}

	/**
	 * A search result pairing a {@link Product} with its similarity score.
	 *
	 * <p>The score indicates how semantically similar the product is to the search query,
	 * with higher values representing greater similarity. For cosine similarity, scores
	 * typically range from 0 (no similarity) to 1 (identical direction in vector space).
	 *
	 * @param product the matched product
	 * @param score   the similarity score between the query vector and the product's embedding
	 */
	public record SearchResult(Product product, float score) {}

	/**
	 * A paginated result containing a subset of products along with pagination metadata.
	 *
	 * @param products the list of products on the current page
	 * @param total    the total number of products in the GigaMap
	 * @param page     the zero-based index of the current page
	 * @param size     the maximum number of products per page
	 */
	public record PageResult(List<Product> products, long total, int page, int size) {}
}
