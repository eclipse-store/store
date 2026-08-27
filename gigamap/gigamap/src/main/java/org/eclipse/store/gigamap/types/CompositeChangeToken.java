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
		if(!(prevEntityHandler instanceof CompositeChangeToken))
		{
			/*
			 * There is no previous state to read keys from: AbstractCompositeBitmapIndex#getChangeHandler
			 * yields the NullChangeChandler for a null previous entity, which is what GigaMap#set is handed
			 * when it fills a slot that #removeById emptied (restoring an entity at its own id). Every other
			 * ChangeHandler implementation copes with a foreign previous handler by routing the de-indexing
			 * through #removeFromIndex instead of reading keys off it, so this does the same: de-index
			 * whatever the previous handler stands for (nothing, for the null handler), then let the index
			 * add the new keys, which is what it does for absent old keys. Formerly the cast below was
			 * unguarded and threw a ClassCastException here.
			 */
			prevEntityHandler.removeFromIndex(entityId);
			this.index.internalHandleChanged(null, entityId, this.keys);

			return;
		}

		this.index.internalHandleChanged(((CompositeChangeToken<?, KS, K>)prevEntityHandler).keys, entityId, this.keys);
	}
	
}
