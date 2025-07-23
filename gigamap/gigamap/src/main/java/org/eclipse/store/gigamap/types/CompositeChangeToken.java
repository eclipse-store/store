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

import org.eclipse.serializer.equality.Equalator;


/**
 * Represents a composite change handler for managing changes in an index. This class
 * is responsible for handling entity updates and removals from an {@code AbstractCompositeBitmapIndex}
 * using a set of composite keys and a custom {@code Equalator} for key comparison.
 *
 * @param <E>  the type of the entities managed by this change token
 * @param <KS> the type of the composite keys used within the index
 * @param <K>  the type of the individual key components in the composite keys
 */
public final class CompositeChangeToken<E, KS, K> implements ChangeHandler
{
	private final AbstractCompositeBitmapIndex<E, KS, K> index        ;
	private final KS                    keys         ;
	private final Equalator<? super KS> keysEqualator;
	
	CompositeChangeToken(final AbstractCompositeBitmapIndex<E, KS, K> index, final KS keys, final Equalator<? super KS> keysEqualator)
	{
		super();
		this.index         = index        ;
		this.keys          = keys         ;
		this.keysEqualator = keysEqualator;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public boolean isEqual(final ChangeHandler other)
	{
		return other instanceof CompositeChangeToken
			&& this.keysEqualator.equal(
			this.keys,
			((CompositeChangeToken<?, KS, K>)other).keys
		)
			;
	}
	
	@Override
	public void removeFromIndex(final long entityId)
	{
		// marks stateChangeChildren internally
		this.index.internalRemoveForKeys(entityId, this.keys);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void changeInIndex(final long entityId, final ChangeHandler prevEntityHandler)
	{
		this.index.internalHandleChanged(((CompositeChangeToken<?, KS, K>)prevEntityHandler).keys, entityId, this.keys);
	}
	
}
