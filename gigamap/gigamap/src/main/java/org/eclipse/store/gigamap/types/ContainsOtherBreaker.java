package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2026 MicroStream Software
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
 * Variant of {@link ContainsBreaker} that ignores a single excluded entity id: it terminates the
 * "contains" scan as soon as a matching entity id <em>other than</em> the excluded one is found,
 * while the excluded id itself is skipped (scanning continues).
 * <p>
 * Used by the unique-constraint check during an update so that an entity colliding only with its own
 * (possibly stale) index entry is not mistaken for a duplicate; see
 * {@link BitmapIndex.Internal#internalContains(Object, long)}.
 *
 * @param <E> The type of elements stored in the {@code GigaMap}.
 */
public final class ContainsOtherBreaker<E> implements EntityResolver<E>
{
	private final long excludedEntityId;

	ContainsOtherBreaker(final long excludedEntityId)
	{
		super();
		this.excludedEntityId = excludedEntityId;
	}

	@Override
	public GigaMap<E> parent()
	{
		return null;
	}

	@Override
	public E get(final long entityId)
	{
		if(entityId != this.excludedEntityId)
		{
			// a matching id other than the excluded one means the key is really contained elsewhere
			throw X.BREAK();
		}

		// the excluded entity's own entry is not a duplicate: skip it and keep scanning
		return null;
	}
}
