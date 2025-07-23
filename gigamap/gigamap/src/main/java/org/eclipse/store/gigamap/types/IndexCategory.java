package org.eclipse.store.gigamap.types;

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

/**
 * Represents a category of indices for managing and organizing entities within
 * a {@link GigaMap}. This interface provides methods to specify the type of
 * index group and to create an index group for a given {@link GigaMap}.
 *
 * @param <E> the type of entities being managed
 * @param <I> the specific implementation of {@link IndexGroup} associated with this category
 */
public interface IndexCategory<E, I extends IndexGroup<E>>
{
	/**
	 * Retrieves the class type of the specific {@link IndexGroup} implementation
	 * associated with this index category. This method provides information about
	 * the runtime type of the index group.
	 *
	 * @return the class object representing the type of the index group
	 *         implementation associated with this category
	 */
	public Class<? extends I> indexType();
	
	/**
	 * Creates an internal index group for the specified {@link GigaMap}.
	 *
	 * @param gigaMap the {@link GigaMap} instance for which the index group is created
	 * @return an internal representation of the index group
	 */
	public IndexGroup.Internal<E> createIndexGroup(GigaMap<E> gigaMap);
	
}
