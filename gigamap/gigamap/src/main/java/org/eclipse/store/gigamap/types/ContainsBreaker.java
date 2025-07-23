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

import org.eclipse.serializer.util.X;


/**
 * The ContainsBreaker class is an implementation of the {@link BitmapResult.Resolver} interface.
 * This class is designed to efficiently terminate a "contains" query as soon as a match
 * for the specified entity ID is found.
 *
 * @param <E> The type of elements stored in the {@code GigaMap}.
 */
public final class ContainsBreaker<E> implements BitmapResult.Resolver<E>
{
	@Override
	public final GigaMap<E> parent()
	{
		return null;
	}
	
	@Override
	public final E get(final long entityId)
	{
		// when the first matching entityId has been found, a contains query is satisfied
		throw X.BREAK();
	}
}
