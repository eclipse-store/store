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

@RestController
@RequestMapping("/products")
public class ProductController
{
	private final ProductService productService;

	public ProductController(final ProductService productService)
	{
		this.productService = productService;
	}

	@GetMapping
	public PageResult list(
		@RequestParam(defaultValue = "0") final int page,
		@RequestParam(defaultValue = "20") final int size
	)
	{
		return this.productService.list(page, size);
	}

	@GetMapping("/search")
	public List<SearchResult> search(
		@RequestParam final String query,
		@RequestParam(defaultValue = "5") final int k
	)
	{
		return this.productService.search(query, k);
	}

	@PostMapping("/add")
	public List<Product> addRandom(@RequestParam(defaultValue = "1") final int count)
	{
		return this.productService.addRandomProducts(count);
	}
}
