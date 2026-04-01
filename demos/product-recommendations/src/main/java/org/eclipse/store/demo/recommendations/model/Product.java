package org.eclipse.store.demo.recommendations.model;

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

import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Represents a product in the product catalog.
 *
 * <p>This record serves as the primary domain entity for the product recommendations demo.
 * Products are stored in a {@link org.eclipse.store.gigamap.types.GigaMap GigaMap} and
 * indexed via a {@link org.eclipse.store.gigamap.jvector.VectorIndex VectorIndex} for
 * semantic similarity search.
 *
 * <p>For vector embedding purposes, only the {@link #name()} and {@link #category()} fields
 * are used to generate the textual representation (see
 * {@link org.eclipse.store.demo.recommendations.vectorizer.ProductVectorizer}), as they
 * capture the semantic identity of a product. The {@link #price()} and {@link #inStock()}
 * fields are available for display and filtering but do not influence similarity search.
 *
 * <h2>JSON Mapping</h2>
 * <p>This record is deserialized from JSON using Jackson. The {@code in_stock} JSON field
 * is mapped to the {@link #inStock()} accessor via {@link JsonProperty @JsonProperty("in_stock")}.
 *
 * <h2>Example JSON</h2>
 * <pre>{@code
 * {
 *   "name": "Wireless Headphones",
 *   "category": "Electronics",
 *   "price": 59.99,
 *   "in_stock": true
 * }
 * }</pre>
 *
 * @param name    the display name of the product (e.g., "Wireless Headphones")
 * @param category the product category (e.g., "Electronics", "Home & Garden")
 * @param price   the product price in the default currency
 * @param inStock whether the product is currently available in stock
 *
 * @see org.eclipse.store.demo.recommendations.vectorizer.ProductVectorizer
 * @see org.eclipse.store.demo.recommendations.service.ProductService
 */
public record Product(
	String name,
	String category,
	double price,
	@JsonProperty("in_stock") boolean inStock
)
{
}
