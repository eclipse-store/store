package org.eclipse.store.demo.recommendations.config;

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

@Configuration
public class GigaMapConfig
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
	public ProductVectorizer productVectorizer(final EmbeddingModel embeddingModel)
	{
		return new ProductVectorizer(embeddingModel);
	}

	@Bean
	public GigaMap<Product> productGigaMap()
	{
		return GigaMap.New();
	}

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
