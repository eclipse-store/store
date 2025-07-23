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

import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;


/**
 * The {@code HashingBitmapIndex} class represents an immutable implementation of a bitmap index
 * based on hashing. This index uses an {@link Indexer} to hash elements into buckets and associate
 * them with keys. It provides efficient indexing operations within bitmap indices management.
 *
 * @param <E> the type of the entities managed by this index
 * @param <K> the type of the keys used for indexing
 */
public final class HashingBitmapIndex<E, K>
	extends AbstractBitmapIndexHashing<E, E, K>
	implements BitmapIndex.TopLevel<E, K>
{
	static BinaryTypeHandler<HashingBitmapIndex<?, ?>> provideTypeHandler()
	{
		return BinaryHandlerHashingBitmapIndex.New();
	}
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	HashingBitmapIndex(
		final BitmapIndices<E>                     parent      ,
		final String                               name        ,
		final Indexer<? super E, K>                indexer     ,
		final EqHashTable<K, BitmapEntry<E, E, K>> entries     ,
		final boolean                              stateChanged
	)
	{
		super(parent, name, indexer, entries, stateChanged);
	}
	
	HashingBitmapIndex(final BitmapIndices<E> parent, final String name, final Indexer<? super E, K> indexer)
	{
		super(parent, name, indexer);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public boolean isSuitableAsUniqueConstraint()
	{
		// hashing each different entity uniquely means creating a hashtable of entities plus useless indexing overhead.
		return false;
	}
	
}
