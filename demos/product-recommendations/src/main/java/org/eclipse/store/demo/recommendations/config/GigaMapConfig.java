package org.eclipse.store.demo.recommendations.config;

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

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.eclipse.store.demo.recommendations.model.Product;
import org.eclipse.store.demo.recommendations.vectorizer.ProductVectorizer;
import org.eclipse.store.gigamap.jvector.VectorIndex;
import org.eclipse.store.gigamap.jvector.VectorIndexConfiguration;
import org.eclipse.store.gigamap.jvector.VectorIndices;
import org.eclipse.store.gigamap.jvector.VectorSimilarityFunction;
import org.eclipse.store.gigamap.types.GigaMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration class that sets up the GigaMap storage layer, vector indexing,
 * and embedding model infrastructure for the product recommendations demo.
 *
 * <p>This configuration wires together the following components:
 * <ol>
 *   <li>An {@link EmbeddingModel} backed by Ollama for generating dense vector embeddings</li>
 *   <li>A {@link ProductVectorizer} that converts {@link Product} entities into embeddings</li>
 *   <li>A {@link GigaMap} instance for high-performance in-memory product storage</li>
 *   <li>A {@link VectorIndex} for approximate nearest-neighbor (ANN) similarity search</li>
 * </ol>
 *
 * <h2>Embedding Model</h2>
 * <p>The embedding model is configured via the following application properties:
 * <ul>
 *   <li>{@code ollama.base-url} - the base URL of the Ollama server (e.g., {@code http://localhost:11434})</li>
 *   <li>{@code ollama.model-name} - the name of the embedding model to use (e.g., {@code all-minilm})</li>
 * </ul>
 *
 * <h2>Vector Index Configuration</h2>
 * <p>The vector index is configured with:
 * <ul>
 *   <li><b>Dimension:</b> 384 — matching the output dimensionality of typical small embedding models
 *       (e.g., all-MiniLM-L6-v2)</li>
 *   <li><b>Similarity function:</b> {@link VectorSimilarityFunction#COSINE COSINE} — measures the
 *       cosine of the angle between two vectors, which is well-suited for normalized text embeddings
 *       where direction (semantic meaning) matters more than magnitude</li>
 *   <li><b>Index name:</b> {@code "product-embeddings"} — a logical identifier for the vector index
 *       within the GigaMap</li>
 * </ul>
 *
 * @see ProductVectorizer
 * @see ProductService
 */
@Configuration
public class GigaMapConfig
{
	/**
	 * Creates an Ollama-backed embedding model for generating vector embeddings.
	 *
	 * <p>The model connects to a running Ollama instance and uses the specified model
	 * for text-to-vector embedding. This bean is shared across the application for both
	 * product vectorization and query embedding.
	 *
	 * @param baseUrl   the Ollama server base URL (from {@code ollama.base-url} property)
	 * @param modelName the embedding model name (from {@code ollama.model-name} property)
	 * @return a configured {@link OllamaEmbeddingModel} instance
	 */
	@Bean
	public EmbeddingModel embeddingModel(
		@Value("${ollama.base-url}") final String baseUrl,
		@Value("${ollama.model-name}") final String modelName
	)
	{
		return OllamaEmbeddingModel.builder()
			.baseUrl(baseUrl)
			.modelName(modelName)
			.build();
	}

	/**
	 * Creates the {@link ProductVectorizer} that transforms products into vector embeddings.
	 *
	 * @param embeddingModel the embedding model used for vectorization
	 * @return a new {@link ProductVectorizer} instance
	 *
	 * @see ProductVectorizer
	 */
	@Bean
	public ProductVectorizer productVectorizer(final EmbeddingModel embeddingModel)
	{
		return new ProductVectorizer(embeddingModel);
	}

	/**
	 * Creates the {@link GigaMap} instance that serves as the primary in-memory store
	 * for {@link Product} entities.
	 *
	 * <p>GigaMap provides high-performance storage with support for vector indexing.
	 * Products added to this map are automatically indexed by any registered
	 * {@link VectorIndex} instances.
	 *
	 * @return a new, empty {@link GigaMap} for products
	 */
	@Bean
	public GigaMap<Product> productGigaMap()
	{
		return GigaMap.New();
	}

	/**
	 * Creates and registers a {@link VectorIndex} on the product {@link GigaMap} for
	 * semantic similarity search.
	 *
	 * <p>This method performs two key setup steps:
	 * <ol>
	 *   <li>Registers a {@link VectorIndices} category on the GigaMap's index registry,
	 *       enabling vector-based indexing for the map</li>
	 *   <li>Adds a named vector index ({@code "product-embeddings"}) with a 384-dimensional
	 *       cosine similarity configuration and the provided vectorizer</li>
	 * </ol>
	 *
	 * <p>Once registered, products added to the GigaMap are automatically vectorized and
	 * indexed, making them searchable via nearest-neighbor queries.
	 *
	 * @param gigaMap    the product GigaMap to index
	 * @param vectorizer the vectorizer used to generate product embeddings
	 * @return the configured {@link VectorIndex} for product similarity search
	 */
	@Bean
	public VectorIndex<Product> productVectorIndex(
		final GigaMap<Product> gigaMap,
		final ProductVectorizer vectorizer
	)
	{
		final VectorIndices<Product> vectorIndices = gigaMap.index().register(VectorIndices.Category());

		final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
			.dimension(384)
			.similarityFunction(VectorSimilarityFunction.COSINE)
			.build();

		return vectorIndices.add("product-embeddings", config, vectorizer);
	}
}
