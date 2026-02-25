package org.eclipse.store.demo.recommendations.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Product(
	String name,
	String category,
	double price,
	@JsonProperty("in_stock") boolean inStock
)
{
}
