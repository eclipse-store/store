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

import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.AbstractBinaryHandlerCustom;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.*;
import org.eclipse.serializer.util.X;


/**
 * Handles binary serialization and deserialization for {@link AbstractBitmapIndexBinary} types.
 * This abstract class provides concrete implementations for persistence-related operations
 * such as storing and loading object states, iterating over references within serialized data,
 * and managing internal state updates. It extends {@link AbstractBinaryHandlerBitmapIndexAbstract}
 * to provide additional support specifically for binary-backed bitmap index structures.
 *
 * @param <I> The concrete type of the {@link AbstractBitmapIndexBinary} this handler works with.
 */
public abstract class AbstractBinaryHandlerAbstractBitmapIndexBinary<I extends  AbstractBitmapIndexBinary<?, ?>>
extends AbstractBinaryHandlerBitmapIndexAbstract<I>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
	
	private static final long
		BINARY_OFFSET_indexer = binaryOffsetSubClassFields()                              ,
		BINARY_OFFSET_entries = BINARY_OFFSET_indexer        + Binary.objectIdByteLength(),
		                      
		MEMORY_OFFSET_indexer = getClassDeclaredFieldOffset(AbstractBitmapIndexBinary.class, "indexer"),
		MEMORY_OFFSET_entries = getClassDeclaredFieldOffset(AbstractBitmapIndexBinary.class, "entries")
	;
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	AbstractBinaryHandlerAbstractBitmapIndexBinary(final Class<I> type)
	{
		super(
			type,
			CustomFields(
				CustomField(BinaryIndexer.class, "indexer"),
				AbstractBinaryHandlerCustom.Complex("entries",
					AbstractBinaryHandlerCustom.CustomField(BitmapEntry.class, "entry")
				)
			)
		);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public void internalStore(
		final Binary                          data    ,
		final I                               instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		final BitmapEntry<?, ?, ?>[] entries = instance.entries();
		data.storeReferences(this.typeId(), objectId, BINARY_OFFSET_entries, handler, entries, 0, entries.length);
		
		// store super class' fields AFTER the entity header has been written (and buffer space has been reserved).
		super.internalStore(data, instance, objectId, handler);
		data.storeReference(BINARY_OFFSET_indexer, handler, XMemory.getObject(instance, MEMORY_OFFSET_indexer));
	}
	
	@Override
	public abstract I create(Binary data, PersistenceLoadHandler handler);
	
	@SuppressWarnings("unchecked")
	private static <E> BinaryIndexer<? super E> getIndexer(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (BinaryIndexer<? super E>)data.readReference(BINARY_OFFSET_indexer, handler);
	}
	
	@Override
	protected <E> void internalUpdateState(
		final Binary                 data    ,
		final I                      instance,
		final PersistenceLoadHandler handler
	)
	{
		super.internalUpdateState(data, instance, handler);
		
		final int arrayLength = X.checkArrayRange(data.getListElementCountReferences(BINARY_OFFSET_entries));
		final BitmapEntry<E, E, Long>[] array = BitmapEntry.createEntriesArray(arrayLength);
		data.collectElementsIntoArray(BINARY_OFFSET_entries, handler, array);

		final BinaryIndexer<? super E> indexer = getIndexer(data, handler);
		XMemory.setObject(instance, MEMORY_OFFSET_indexer, indexer);
		XMemory.setObject(instance, MEMORY_OFFSET_entries, array);

		this.setBackReferences(instance, array);
		instance.initializeTransientState();
	}
	
	protected <E> void setBackReferences(
		final I                         instance,
		final BitmapEntry<E, E, Long>[] entries
	)
	{
		@SuppressWarnings("unchecked")
		final AbstractBitmapIndexBinary<E, E> casted = (AbstractBitmapIndexBinary<E, E>)instance;
		
		for(int i = 0; i < entries.length; i++)
		{
			if(entries[i] == null)
			{
				continue;
			}
			entries[i].internalSetBackReferences(casted, AbstractBitmapIndexBinary.arrayIndexToKey(i));
		}
	}
	
	@Override
	public void iterateInstanceReferences(final I instance, final PersistenceFunction iterator)
	{
		super.iterateInstanceReferences(instance, iterator);
		iterator.apply(instance.indexer);
		Persistence.iterateReferences(iterator, instance.entries);
	}

	@Override
	public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		super.iterateLoadableReferences(data, iterator);
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_indexer));
		data.iterateListElementReferences(BINARY_OFFSET_entries, iterator);
	}
	
}
