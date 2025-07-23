package org.eclipse.store.gigamap.misc.it;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

public class Product
{
	private final String name;
	private final double price;
	
	public Product(final String name, final double price)
	{
		super();
		this.name  = name;
		this.price = price;
	}
	
	public String name()
	{
		return this.name;
	}
	
	public double price()
	{
		return this.price;
	}

	@Override
	public String toString()
	{
		return "Product [name=" + this.name + ", price=" + this.price + "]";
	}
	
}
