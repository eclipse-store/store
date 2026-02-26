package org.eclipse.store.demo.recommendations.vectorizer;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.eclipse.store.demo.recommendations.model.Product;
import org.eclipse.store.gigamap.jvector.Vectorizer;

import java.util.List;

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
		return this.embeddingModel.embed(toText(product)).content().vector();
	}

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

	private static String toText(final Product product)
	{
		return product.name() + " " + product.category();
	}
}
