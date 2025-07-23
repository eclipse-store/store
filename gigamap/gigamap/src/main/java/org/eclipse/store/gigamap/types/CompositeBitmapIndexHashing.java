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

import org.eclipse.serializer.collections.XArrays;
import org.eclipse.serializer.hashing.HashEqualator;
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;

import java.util.Arrays;
import java.util.function.Predicate;


/**
 * CompositeBitmapIndexHashing is an implementation of {@link AbstractCompositeBitmapIndex} that uses
 * hashing to manage composite bitmap indices. It provides efficient methods for managing
 * and querying bitmap indices with hash-based keys.
 * <p>
 * This class organizes entities into a structure of bitmap indices, where each sub-index
 * is managed independently, and supports dynamic resizing of sub-indices as necessary to
 * accommodate entities and their keys.
 *
 * @param <E> the type of entity maintained by this composite bitmap index
 */
public class CompositeBitmapIndexHashing<E> extends AbstractCompositeBitmapIndex<E, Object[], Object>
{
	static BinaryTypeHandler<CompositeBitmapIndexHashing<?>> provideTypeHandler()
	{
		return BinaryHandlerCompositeBitmapIndexHashing.New();
	}
	
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private transient Object[] carrier;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	protected CompositeBitmapIndexHashing(
		final BitmapIndices<E>                   parent      ,
		final String                             name        ,
		final HashingCompositeIndexer<? super E> indexer     ,
		final boolean                            stateChanged
	)
	{
		super(parent, name, indexer, stateChanged);
	}
	
	protected CompositeBitmapIndexHashing(
		final BitmapIndices<E>                   parent ,
		final String                             name   ,
		final HashingCompositeIndexer<? super E> indexer
	)
	{
		this(parent, name, indexer, true);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	protected final Object[] carrier()
	{
		return this.carrier;
	}
	
	@Override
	protected final boolean isEmpty(final Object[] keys, final int i)
	{
		// keys array could theoretically have less elements than there are sub indices, hence the length check.
		return i >= keys.length || keys[i] == null;
	}
	
	@Override
	protected final void clear(final Object[] keys, final int i)
	{
		keys[i] = null;
	}
	
	@Override
	protected void initializeTransientState()
	{
		// carrier just starts at length 0 and gets enlarged as needed.
		this.carrier = new Object[0];
	}
	
	@Override
	protected CompositePredicate<Object[]> wrap(final Predicate<? super Object[]> subject, final Object[] carrier)
	{
		return CompositePredicate.WrapEntryBased(subject, carrier);
	}
	
	@Override
	protected boolean isOversized(final Object[] keys)
	{
		for(int i = this.subIndices.length; i < keys.length; i++)
		{
			if(keys[i] != null)
			{
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	protected void ensureSubIndices(final Object[] keys)
	{
		if(this.subIndices.length >= keys.length)
		{
			// already enough sub indices, so abort.
			return;
		}
		
		this.subIndices = this.increaseSubIndices(keys.length);
		this.markStateChangeInstance();
		this.carrier = keys.clone();
	}
	
	protected Sub<E, Object[], Object>[] increaseSubIndices(final int newCount)
	{
		final Sub<E, Object[], Object>[] array = XArrays.enlarge(this.subIndices, newCount);
		for(int i = this.subIndices.length; i < array.length; i++)
		{
			array[i] = new SubBitmapIndexHashing<>(this.parent(), this.name(), (HashEqualator<Object>)this.indexer.provideSubIndexHashEqualator(i), i);
		}
		
		return array;
	}
	
	@Override
	protected final void clearCarrier()
	{
		Arrays.fill(this.carrier, null);
	}
	
	@Override
	protected CompositeChangeToken<E, Object[], Object> createChangeToken(final E oldEntity)
	{
		// may not use the carrier array since that is used for the newEntity, so it would be compared against itself.
		final Object[] keys = this.indexer.index(oldEntity);
		
		return new CompositeChangeToken<>(this, keys, Arrays::equals);
	}
	
	@Override
	public <S extends E> Condition<S> is(final Object[] key)
	{
		return super.is(new CompositePredicate.ObjectSampleBased(key));
	}
}
