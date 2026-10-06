package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
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

/**
 * Represents an operation that accepts a primitive {@code long} index key and the id of the entity
 * it belongs to. The primitive counterpart of an {@code ObjLongConsumer<Long>}, so that enumerating
 * the pairs of a binary index boxes neither the key nor the entity id.
 * This is a functional interface and can therefore be used as the assignment target for a lambda
 * expression or method reference.
 * <p>
 * The functional method is {@link #accept(long, long)}.
 *
 * @see BitmapIndex#iterateLongKeyEntityPairs(LongKeyEntityConsumer)
 */
@FunctionalInterface
public interface LongKeyEntityConsumer
{
	/**
	 * Performs an operation on the given key and entity id.
	 *
	 * @param key      the index key of the entity
	 * @param entityId the id of the entity (its position in the owning {@code GigaMap})
	 */
	public void accept(long key, long entityId);
}
