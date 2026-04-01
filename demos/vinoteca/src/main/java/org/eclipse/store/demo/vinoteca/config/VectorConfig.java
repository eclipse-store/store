package org.eclipse.store.demo.vinoteca.config;

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

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.eclipse.store.demo.vinoteca.index.WineVectorizer;
import org.eclipse.store.demo.vinoteca.model.DataRoot;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.gigamap.jvector.VectorIndex;
import org.eclipse.store.gigamap.jvector.VectorIndexConfiguration;
import org.eclipse.store.gigamap.jvector.VectorIndices;
import org.eclipse.store.gigamap.jvector.VectorSimilarityFunction;
import org.eclipse.store.gigamap.types.GigaMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "vinoteca.vector-enabled", havingValue = "true", matchIfMissing = false)
public class VectorConfig
{
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

	@Bean
	public WineVectorizer wineVectorizer(final EmbeddingModel embeddingModel)
	{
		return new WineVectorizer(embeddingModel);
	}

	@Bean
	public VectorIndex<Wine> wineVectorIndex(
		final DataRoot       dataRoot,
		final WineVectorizer vectorizer
	)
	{
		final VectorIndices<Wine> vectorIndices = dataRoot.getWines().index().register(VectorIndices.Category());
		final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
			.dimension(384)
			.similarityFunction(VectorSimilarityFunction.COSINE)
			.build();
		return vectorIndices.add("wine-embeddings", config, vectorizer);
	}
}
