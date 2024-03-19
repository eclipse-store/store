
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



import java.util.Collection;
import java.util.Optional;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;


@ApplicationScoped
public class ProductRepositoryStorage implements ProductRepository
{
	private static final Logger LOGGER = Logger.getLogger(ProductRepositoryStorage.class.getName());
	
	@Inject
	private Inventory           inventory;
	
	@Override
	public Collection<Product> getAll()
	{
		return this.inventory.getProducts();
	}
	
	@Override
	public Product save(final Product item)
	{
		this.inventory.add(item);
		return item;
	}
	
	@Override
	public Optional<Product> findById(final long id)
	{
		LOGGER.info("Finding the item by id: " + id);
		return this.inventory.findById(id);
	}
	
	@Override
	public void deleteById(final long id)
	{
		this.inventory.deleteById(id);
	}
}
