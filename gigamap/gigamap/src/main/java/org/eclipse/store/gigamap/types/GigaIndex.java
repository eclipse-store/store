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
 * Represents an interface for indexing mechanisms used within a GigaMap.
 * A GigaIndex facilitates efficient data retrieval and operations based on
 * specific indexing logic.
 *
 * @param <E> the type of elements managed by the index
 */
public interface GigaIndex<E> extends GigaMap.Component<E>
{
	/**
	 * Retrieves the name of this index, which identifies the indexing mechanism used.
	 *
	 * @return the name of the index as a string
	 */
	public String name();
	
	/**
	 * Determines whether the index is suitable to be used as a unique constraint.
	 * A unique constraint ensures that indexed elements are unique within the context
	 * of the index mechanism.
	 *
	 * @return true if the index is suitable as a unique constraint, false otherwise
	 */
	public boolean isSuitableAsUniqueConstraint();
	
}
