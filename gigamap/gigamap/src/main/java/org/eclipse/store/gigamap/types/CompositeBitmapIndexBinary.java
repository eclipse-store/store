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

import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.XArrays;
import org.eclipse.serializer.collections.types.XGettingList;
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;

import java.util.Arrays;
import java.util.function.Predicate;


/**
 * The {@code CompositeBitmapIndexBinary} class is a specialized implementation of
 * {@link AbstractCompositeBitmapIndex} designed for binary-based composite indexing.
 * This class maintains a carrier array of type {@code long[]} and provides functionality
 * to handle binary-structured indices for entities.
 *
 * @param <E> the type of elements maintained by this index
 */
public class CompositeBitmapIndexBinary<E> extends AbstractCompositeBitmapIndex<E, long[], Long>
{
	static BinaryTypeHandler<CompositeBitmapIndexBinary<?>> provideTypeHandler()
	{
		return BinaryHandlerCompositeBitmapIndexBinary.New();
	}
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private transient long[] carrier;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	protected CompositeBitmapIndexBinary(
		final BitmapIndices<E>                  parent      ,
		final String                            name        ,
		final BinaryCompositeIndexer<? super E> indexer     ,
		final boolean                           stateChanged
	)
	{
		super(parent, name, indexer, stateChanged);
	}
	
	protected CompositeBitmapIndexBinary(
		final BitmapIndices<E>                  parent ,
		final String                            name   ,
		final BinaryCompositeIndexer<? super E> indexer
	)
	{
		this(parent, name, indexer, true);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	protected final long[] carrier()
	{
		return this.carrier;
	}
	
	@Override
	protected final boolean isEmpty(final long[] keys, final int i)
	{
		// keys array could theoretically have less elements than there are sub indices, hence the length check.
		return i >= keys.length || keys[i] == 0L;
	}
	
	@Override
	protected final void clear(final long[] keys, final int i)
	{
		keys[i] = 0L;
	}
	
	@Override
	protected void initializeTransientState()
	{
		// carrier just starts at length 0 and gets enlarged as needed.
		this.carrier = new long[0];
	}
	
	@Override
	protected CompositePredicate<long[]> wrap(final Predicate<? super long[]> subject, final long[] carrier)
	{
		return CompositePredicate.WrapBinaryBased(subject, carrier);
	}
	
	@Override
	protected boolean isOversized(final long[] keys)
	{
		for(int i = this.subIndices.length; i < keys.length; i++)
		{
			if(keys[i] != 0L)
			{
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	protected void addEntryResults(
		final int                        subPosition,
		final CompositePredicate<long[]> predicate  ,
		final BulkList<BitmapResult> subResults
	)
	{
		this.subIndices[subPosition].iterateEntries(e ->
		{
			// binary logic: selected entries are collected as results and discarded entries must be collected as inverted results.
			if(predicate.test(subPosition, e.key()))
			{
				subResults.add(e.createResult());
			}
			else
			{
				subResults.add(new BitmapResult.Not(e.createResult()));
			}
		});
	}
	
	@Override
	protected void addSubResult(
		final XGettingList<BitmapResult>[] results,
		final int                          i      ,
		final BulkList<BitmapResult> subResultAnds
	)
	{
		final XGettingList<BitmapResult> subResults = results[i];
		
		// binary logic means all selected entry results must be combined via AND logic (e.g. value 5 is bit#0 AND bit#2)
		subResultAnds.add(new BitmapResult.ChainAnd(subResults.toArray(BitmapResult.class)));
	}
	
	@Override
	protected void ensureSubIndices(final long[] keys)
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
	
	protected Sub<E, long[], Long>[] increaseSubIndices(final int newCount)
	{
		final Sub<E, long[], Long>[] array = XArrays.enlarge(this.subIndices, newCount);
		for(int i = this.subIndices.length; i < array.length; i++)
		{
			array[i] = new SubBitmapIndexBinary<>(this.parent(), this.name(), i);
		}
		
		return array;
	}
	
	@Override
	protected final void clearCarrier()
	{
		Arrays.fill(this.carrier, 0L);
	}
	
	@Override
	protected CompositeChangeToken<E, long[], Long> createChangeToken(final E oldEntity)
	{
		// may not use the carrier array since that is used for the newEntity, so it would be compared against itself.
		final long[] keys = this.indexer.index(oldEntity);
		
		return new CompositeChangeToken<>(this, keys, Arrays::equals);
	}
	
	@Override
	public <S extends E> Condition<S> is(final long[] key)
	{
		return super.is(new CompositePredicate.BinarySampleBased(key));
	}
	
}
