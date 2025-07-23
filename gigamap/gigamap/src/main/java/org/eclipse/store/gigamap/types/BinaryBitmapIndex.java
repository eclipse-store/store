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

import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;


/**
 * BinaryBitmapIndex is a class that represents a binary bitmap indexing
 * mechanism which extends the {@link AbstractBitmapIndexBinary} class. This class
 * implements the {@link TopLevel} interface to add specific functionalities
 * related to binary indexing.
 *
 * @param <E> the type of the elements being indexed
 */
public final class BinaryBitmapIndex<E>
	extends AbstractBitmapIndexBinary<E, E>
	implements BitmapIndex.TopLevel<E, Long>
{
	static BinaryTypeHandler<BinaryBitmapIndex<?>> provideTypeHandler()
	{
		return BinaryHandlerBinaryBitmapIndex.New();
	}
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	BinaryBitmapIndex(
		final BitmapIndices<E>         parent      ,
		final String                   name        ,
		final BinaryIndexer<? super E> indexer     ,
		final boolean                  stateChanged
	)
	{
		super(parent, name, indexer, stateChanged);
	}
	
	BinaryBitmapIndex(final BitmapIndices<E> parent, final String name, final BinaryIndexer<? super E> indexer)
	{
		this(parent, name, indexer, true);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public final BitmapResult internalQuery(final Long key)
	{
		// only to link the method to the interface declaration to make it findable as an implementor.
		return super.internalQuery(key);
	}
	
}
