package org.eclipse.store.demo.recommendations.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import org.eclipse.store.demo.recommendations.model.Product;
import org.eclipse.store.gigamap.jvector.VectorIndex;
import org.eclipse.store.gigamap.jvector.VectorSearchResult;
import org.eclipse.store.gigamap.types.GigaMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class ProductService
{
	private static final Logger LOG                  = LoggerFactory.getLogger(ProductService.class);
	private static final int    INITIAL_PRODUCT_COUNT = 500;

	private final GigaMap<Product>      gigaMap;
	private final VectorIndex<Product>  vectorIndex;
	private final EmbeddingModel        embeddingModel;
	private final List<Product>         availableProducts;
	private final Random                random = new Random();

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

	public List<SearchResult> search(final String query, final int k)
	{
		final float[] queryVector = this.embeddingModel.embed(query).content().vector();
		final VectorSearchResult<Product> result = this.vectorIndex.search(queryVector, k);

		final List<SearchResult> results = new ArrayList<>();
		for(final VectorSearchResult.Entry<Product> entry : result)
		{
			results.add(new SearchResult(entry.entity(), entry.score()));
		}
		return results;
	}

	public PageResult list(final int page, final int size)
	{
		final long total = this.gigaMap.size();
		final int  skip  = page * size;

		final List<Product> products = this.gigaMap.query().stream().skip(skip).limit(size).toList();

		return new PageResult(products, total, page, size);
	}

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

	public record SearchResult(Product product, float score) {}

	public record PageResult(List<Product> products, long total, int page, int size) {}
}
