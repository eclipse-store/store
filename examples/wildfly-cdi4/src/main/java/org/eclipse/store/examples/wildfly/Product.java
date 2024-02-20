
package org.eclipse.store.examples.wildfly;

/*-
 * #%L
 * EclipseStore Wildfly CDI 4 Example
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */



import java.util.Objects;

import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;


public class Product
{
	private final long   id         ;
	private final String name       ;
	private final String description;
	private final int    rating     ;
	
	@JsonbCreator
	public Product(
		@JsonbProperty("id")          final long   id         ,
		@JsonbProperty("name")        final String name       ,
		@JsonbProperty("description") final String description,
		@JsonbProperty("rating")      final int    rating
	)
	{
		this.id          = id         ;
		this.name        = name       ;
		this.description = description;
		this.rating      = rating     ;
	}
	
	public long getId()
	{
		return this.id;
	}
	
	public String getName()
	{
		return this.name;
	}
	
	public String getDescription()
	{
		return this.description;
	}
	
	public int getRating()
	{
		return this.rating;
	}
	
	@Override
	public boolean equals(final Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(o == null || this.getClass() != o.getClass())
		{
			return false;
		}
		final Product product = (Product)o;
		return this.id == product.id;
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hashCode(this.id);
	}
	
	@Override
	public String toString()
	{
		return "Product{"
			+
			"id="
			+ this.id
			+
			", name='"
			+ this.name
			+ '\''
			+
			", description='"
			+ this.description
			+ '\''
			+
			", rating="
			+ this.rating
			+
			'}';
	}
}
