package org.eclipse.store.demo.vinoteca.index;

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

import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.gigamap.jvector.Vectorizer;

/**
 * Computes vector embeddings for {@link Wine} entities to feed the JVector vector index registered
 * on the wines GigaMap.
 * <p>
 * The vectorizer concatenates a wine's name, grape variety (with underscores translated back into
 * spaces), tasting notes, aroma and food pairing into a single descriptive sentence and forwards
 * that to a LangChain4j {@link OllamaEmbeddingModel} configured against a locally running
 * <a href="https://ollama.com">Ollama</a> instance. The default model used by the demo is
 * {@code all-minilm}, which produces 384-dimensional vectors — the same dimensionality the index
 * is configured for in {@link org.eclipse.store.demo.vinoteca.model.DataRoot DataRoot}.
 * <p>
 * The {@link EmbeddingModel} is held in a {@code transient} field and lazily created via
 * {@link #embeddingModel()} so that the vectorizer can be (de)serialized as part of the GigaMap
 * configuration without dragging an active HTTP client into the persistent graph.
 */
public class WineVectorizer extends Vectorizer<Wine>
{
	private final String baseUrl;
	private final String modelName;

	private transient EmbeddingModel embeddingModel;

	/**
	 * Creates a vectorizer that talks to an Ollama instance.
	 *
	 * @param baseUrl   the base URL of the Ollama server (e.g. {@code "http://localhost:11434"})
	 * @param modelName the embedding model name to use (e.g. {@code "all-minilm"})
	 */
	public WineVectorizer(final String baseUrl, final String modelName)
	{
		this.baseUrl   = baseUrl;
		this.modelName = modelName;
	}

	/**
	 * Lazily constructs and caches the underlying {@link EmbeddingModel}.
	 *
	 * @return the embedding model instance to use for {@link #vectorize}/{@link #vectorizeAll}
	 */
	private EmbeddingModel embeddingModel()
	{
		if (this.embeddingModel == null)
		{
			this.embeddingModel = OllamaEmbeddingModel.builder()
				.baseUrl(this.baseUrl)
				.modelName(this.modelName)
				.build();
		}
		return this.embeddingModel;
	}

	/**
	 * Computes the embedding vector for a single wine.
	 *
	 * @param wine the wine to embed
	 * @return the embedding vector
	 */
	@Override
	public float[] vectorize(final Wine wine)
	{
		return this.embeddingModel().embed(toText(wine)).content().vector();
	}

	/**
	 * Computes embedding vectors for a batch of wines in a single round-trip to Ollama. This is
	 * substantially faster than calling {@link #vectorize(Wine)} in a loop and is what GigaMap
	 * uses when a large number of entries are added at once (e.g. during initial data generation).
	 *
	 * @param entities the wines to embed
	 * @return the embedding vectors in the same order as {@code entities}
	 */
	@Override
	public List<float[]> vectorizeAll(final List<? extends Wine> entities)
	{
		return this.embeddingModel().embedAll(
				entities.stream().map(WineVectorizer::toText).map(TextSegment::from).toList()
			)
			.content()
			.stream()
			.map(Embedding::vector)
			.toList();
	}

	/**
	 * Builds the descriptive sentence that the embedding model is asked to encode. Null fields
	 * are simply skipped so they do not pollute the embedding with the literal {@code "null"}.
	 *
	 * @param wine the source wine
	 * @return a single-line textual description of the wine
	 */
	private static String toText(final Wine wine)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(wine.getName());
		if (wine.getGrapeVariety() != null)
		{
			sb.append(' ').append(wine.getGrapeVariety().name().replace('_', ' '));
		}
		if (wine.getTastingNotes() != null)
		{
			sb.append(' ').append(wine.getTastingNotes());
		}
		if (wine.getAroma() != null)
		{
			sb.append(' ').append(wine.getAroma());
		}
		if (wine.getFoodPairing() != null)
		{
			sb.append(' ').append(wine.getFoodPairing());
		}
		return sb.toString();
	}
}
