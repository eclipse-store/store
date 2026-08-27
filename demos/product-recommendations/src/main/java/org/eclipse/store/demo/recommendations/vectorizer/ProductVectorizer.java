package org.eclipse.store.demo.recommendations.vectorizer;

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

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.eclipse.store.demo.recommendations.model.Product;
import org.eclipse.store.gigamap.jvector.Vectorizer;

import java.util.List;


/**
 * A {@link Vectorizer} implementation that transforms {@link Product} instances into dense vector
 * embeddings using a LangChain4j {@link EmbeddingModel}.
 *
 * <p>Each product is represented as a textual description composed of its {@linkplain Product#name() name}
 * and {@linkplain Product#category() category}, which is then passed to the embedding model to produce
 * a numerical vector. These vectors capture the semantic meaning of products, enabling similarity-based
 * operations such as "find products similar to X" through nearest-neighbor search in the
 * {@link org.eclipse.store.gigamap.jvector.VectorIndex}.
 *
 * <p>This vectorizer operates in <b>computed mode</b> (i.e., {@link #isEmbedded()} returns {@code false}),
 * meaning that vectors are generated on demand via the external embedding model rather than being
 * pre-stored within the {@link Product} entity itself. As a result, the associated
 * {@link org.eclipse.store.gigamap.jvector.VectorIndex} will persist the computed vectors separately
 * in a GigaMap to avoid repeated recomputation.
 *
 * <h2>Text Representation</h2>
 * <p>The textual representation used for embedding is a simple concatenation of the product's name
 * and category, separated by a space (e.g., {@code "Wireless Headphones Electronics"}). This approach
 * ensures that both the identity and the domain of the product contribute to the resulting vector,
 * improving the quality of similarity matches across product boundaries. Note that
 * {@linkplain Product#price() price} and {@linkplain Product#inStock() stock availability} are
 * intentionally excluded, as they are not semantically meaningful for similarity search.
 *
 * <h2>Batch Vectorization</h2>
 * <p>This class overrides {@link #vectorizeAll(List)} to leverage the embedding model's native batch
 * embedding capability ({@link EmbeddingModel#embedAll(List)}), which is typically more efficient than
 * embedding products one at a time. Batch processing reduces the number of round-trips to the
 * underlying model (especially relevant for remote/API-based models) and may enable internal
 * parallelism or batching optimizations within the model implementation.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * EmbeddingModel model = ... // e.g., an ONNX all-MiniLM-L6-v2 model
 * ProductVectorizer vectorizer = new ProductVectorizer(model);
 *
 * Product product = new Product("Wireless Headphones", "Electronics", 59.99, true);
 * float[] vector = vectorizer.vectorize(product);
 *
 * // Or batch vectorize multiple products at once:
 * List<Product> products = List.of(product1, product2, product3);
 * List<float[]> vectors = vectorizer.vectorizeAll(products);
 * }</pre>
 *
 * @see Vectorizer
 * @see Product
 * @see EmbeddingModel
 */
public class ProductVectorizer extends Vectorizer<Product>
{
	private final EmbeddingModel embeddingModel;

	/**
	 * Creates a new {@code ProductVectorizer} backed by the given embedding model.
	 *
	 * @param embeddingModel the LangChain4j embedding model used to convert textual product
	 *                       representations into dense vector embeddings; must not be {@code null}
	 */
	public ProductVectorizer(final EmbeddingModel embeddingModel)
	{
		this.embeddingModel = embeddingModel;
	}

	/**
	 * Converts a single {@link Product} into its vector embedding.
	 *
	 * <p>The product is first transformed into a textual representation (name + category),
	 * which is then embedded using the configured {@link EmbeddingModel}. The resulting
	 * dense vector captures the semantic meaning of the product and can be used for
	 * nearest-neighbor similarity searches.
	 *
	 * @param product the product to vectorize; must not be {@code null}
	 * @return a float array representing the product's embedding vector, whose dimensionality
	 *         is determined by the underlying embedding model
	 */
	@Override
	public float[] vectorize(final Product product)
	{
		return this.embeddingModel.embed(toText(product)).content().vector();
	}

	/**
	 * Converts a list of {@link Product} instances into their vector embeddings using
	 * batch embedding.
	 *
	 * <p>This method overrides the default sequential implementation in {@link Vectorizer} to
	 * take advantage of the embedding model's native {@link EmbeddingModel#embedAll(List)} method.
	 * Batch processing is generally more efficient than individual embedding calls because it:
	 * <ul>
	 *   <li>Reduces the number of round-trips to remote embedding services</li>
	 *   <li>Allows the model to apply internal batching and parallelism optimizations</li>
	 *   <li>Minimizes per-request overhead (connection setup, serialization, etc.)</li>
	 * </ul>
	 *
	 * <p>Each product is first mapped to its textual representation and wrapped in a
	 * {@link TextSegment} before being passed to the model. The order of the returned vectors
	 * corresponds to the order of the input products.
	 *
	 * @param entities the list of products to vectorize; must not be {@code null} or contain
	 *                 {@code null} elements
	 * @return an unmodifiable list of float arrays, where each array is the embedding vector
	 *         for the corresponding product in the input list
	 */
	@Override
	public List<float[]> vectorizeAll(final List<? extends Product> entities)
	{
		return this.embeddingModel.embedAll(
				entities.stream().map(ProductVectorizer::toText).map(TextSegment::from).toList()
			)
			.content()
			.stream()
			.map(Embedding::vector)
			.toList();
	}

	/**
	 * Converts a {@link Product} into its textual representation for embedding.
	 *
	 * <p>The text is formed by concatenating the product's {@linkplain Product#name() name}
	 * and {@linkplain Product#category() category} with a space separator. This representation
	 * is intentionally kept simple to produce clean, focused embeddings that capture the core
	 * semantic identity of the product.
	 *
	 * @param product the product to convert to text; must not be {@code null}
	 * @return a string representation of the product suitable for embedding
	 */
	private static String toText(final Product product)
	{
		return product.name() + " " + product.category();
	}
}
