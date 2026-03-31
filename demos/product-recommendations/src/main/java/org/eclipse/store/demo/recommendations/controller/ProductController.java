package org.eclipse.store.demo.recommendations.controller;

import org.eclipse.store.demo.recommendations.model.Product;
import org.eclipse.store.demo.recommendations.service.ProductService;
import org.eclipse.store.demo.recommendations.service.ProductService.PageResult;
import org.eclipse.store.demo.recommendations.service.ProductService.SearchResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing the product catalog and semantic search API.
 *
 * <p>This controller provides three endpoints under the {@code /products} base path:
 *
 * <table>
 *   <caption>Product API Endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>{@code /products}</td><td>Paginated listing of all products</td></tr>
 *   <tr><td>GET</td><td>{@code /products/search}</td><td>Semantic similarity search</td></tr>
 *   <tr><td>POST</td><td>{@code /products/add}</td><td>Add random products from the catalog</td></tr>
 * </table>
 *
 * <h2>Semantic Search</h2>
 * <p>The {@code /products/search} endpoint accepts a free-text {@code query} parameter and returns
 * the top-{@code k} most semantically similar products. The query string is embedded into a vector
 * using the same embedding model that was used to index the products, and then a nearest-neighbor
 * search is performed against the {@link org.eclipse.store.gigamap.jvector.VectorIndex}. Each result
 * includes the matched {@link Product} and a similarity {@code score} (higher is more similar).
 *
 * <h2>Example Requests</h2>
 * <pre>
 * GET /products?page=0&amp;size=10
 * GET /products/search?query=wireless+headphones&amp;k=5
 * POST /products/add?count=10
 * </pre>
 *
 * @see ProductService
 * @see Product
 */
@RestController
@RequestMapping("/products")
public class ProductController
{
	private final ProductService productService;

	/**
	 * Creates a new {@code ProductController} with the given service.
	 *
	 * @param productService the service handling product storage and search operations
	 */
	public ProductController(final ProductService productService)
	{
		this.productService = productService;
	}

	/**
	 * Returns a paginated list of all products in the GigaMap.
	 *
	 * <p>Products are returned in the iteration order of the underlying
	 * {@link org.eclipse.store.gigamap.types.GigaMap}. Pagination is implemented
	 * via stream skip/limit, so large offsets may be less efficient.
	 *
	 * @param page the zero-based page number (default: {@code 0})
	 * @param size the number of products per page (default: {@code 20})
	 * @return a {@link PageResult} containing the product list and pagination metadata
	 */
	@GetMapping
	public PageResult list(
		@RequestParam(defaultValue = "0") final int page,
		@RequestParam(defaultValue = "20") final int size
	)
	{
		return this.productService.list(page, size);
	}

	/**
	 * Performs a semantic similarity search for products matching the given query.
	 *
	 * <p>The query string is embedded into a vector and compared against the product
	 * vector index using cosine similarity. The top-{@code k} most similar products
	 * are returned, each paired with their similarity score.
	 *
	 * @param query the natural-language search query (e.g., "wireless headphones",
	 *              "kitchen appliances", "running shoes")
	 * @param k     the maximum number of results to return (default: {@code 5})
	 * @return a list of {@link SearchResult} entries ordered by descending similarity score
	 */
	@GetMapping("/search")
	public List<SearchResult> search(
		@RequestParam final String query,
		@RequestParam(defaultValue = "5") final int k
	)
	{
		return this.productService.search(query, k);
	}

	/**
	 * Adds random products from the available product catalog to the GigaMap.
	 *
	 * <p>Products are randomly selected from the remaining pool of unloaded catalog entries
	 * (loaded from {@code products.json} at startup). Once a product is added, it is removed
	 * from the available pool to avoid duplicates. If the pool is exhausted, fewer products
	 * than requested may be returned.
	 *
	 * <p>Added products are automatically vectorized and indexed, making them immediately
	 * available for similarity search.
	 *
	 * @param count the number of random products to add (default: {@code 1})
	 * @return the list of products that were actually added
	 */
	@PostMapping("/add")
	public List<Product> addRandom(@RequestParam(defaultValue = "1") final int count)
	{
		return this.productService.addRandomProducts(count);
	}
}
