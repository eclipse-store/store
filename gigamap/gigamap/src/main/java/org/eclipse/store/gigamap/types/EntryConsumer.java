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
 * Represents an operation that accepts a long entity ID and the corresponding element,
 * performing an action on the given inputs. This is a functional interface
 * and can therefore be used as the assignment target for a lambda expression
 * or method reference.
 * <p>
 * The functional method is {@link #accept(long, Object)}.
 *
 * @param <E> the type of the element being consumed
 */
@FunctionalInterface
public interface EntryConsumer<E>
{
	/**
	 * Performs an operation on the given entity ID and the corresponding element.
	 *
	 * @param entityId the ID associated with the entity
	 * @param element the element to be consumed
	 */
	public void accept(long entityId, E element);
}
