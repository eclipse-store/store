package org.eclipse.store.demo.recommendations.vectorizer;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.eclipse.store.demo.recommendations.model.Product;
import org.eclipse.store.gigamap.jvector.Vectorizer;

public class ProductVectorizer extends Vectorizer<Product>
{
	private final EmbeddingModel embeddingModel;

	public ProductVectorizer(final EmbeddingModel embeddingModel)
	{
		this.embeddingModel = embeddingModel;
	}

	@Override
	public float[] vectorize(final Product product)
	{
		return this.embeddingModel.embed(product.name()).content().vector();
	}
}
