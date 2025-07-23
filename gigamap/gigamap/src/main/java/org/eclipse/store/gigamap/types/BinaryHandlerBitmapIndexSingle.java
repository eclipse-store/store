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
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceFunction;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceReferenceLoader;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;


/**
 * BinaryHandlerBitmapIndexSingle is a concrete implementation of
 * {@link AbstractBinaryHandlerBitmapIndexAbstract} designed specifically for handling the serialization,
 * deserialization, and state management of {@link SingleBitmapIndex} instances.
 * This handler manages specific references and fields related to {@link SingleBitmapIndex}, including
 * associated indexers and entries.
 * <p>
 * This class provides mechanisms for storing entity state, retrieving references, creating new
 * instances, and updating the internal state from binary data representation. Additionally,
 * it supports iterating over references for persistence operations.
 * <p>
 * This implementation extends the behavior of {@link AbstractBinaryHandlerBitmapIndexAbstract} by
 * defining {@link SingleBitmapIndex}-specific binary offsets, memory offsets, and custom logic for managing
 * indexer and entry references.
 */
public class BinaryHandlerBitmapIndexSingle extends AbstractBinaryHandlerBitmapIndexAbstract<SingleBitmapIndex<?>>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
	
	private static final long
		BINARY_OFFSET_indexer = binaryOffsetSubClassFields()                              ,
		BINARY_OFFSET_entry   = BINARY_OFFSET_indexer        + Binary.objectIdByteLength(),
		BINARY_LENGTH         = BINARY_OFFSET_entry          + Binary.objectIdByteLength(),
	
		MEMORY_OFFSET_indexer = getClassDeclaredFieldOffset(genericType(), "indexer"),
		MEMORY_OFFSET_entry   = getClassDeclaredFieldOffset(genericType(), "entry"  )
	;
	
	@SuppressWarnings("all")
	public static final Class<SingleBitmapIndex<?>> genericType()
	{
		// no idea how to get ".class" to work otherwise in conjunction with generics.
		return (Class)SingleBitmapIndex.class;
	}
	
	
	
	public static BinaryHandlerBitmapIndexSingle New()
	{
		return new BinaryHandlerBitmapIndexSingle();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	BinaryHandlerBitmapIndexSingle()
	{
		super(
			genericType(),
			CustomFields(
				CustomField(Indexer.class    , "indexer"),
				CustomField(BitmapEntry.class, "entry"  )
			)
		);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public void internalStore(
		final Binary                          data    ,
		final SingleBitmapIndex<?>            instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		data.storeEntityHeader(BINARY_LENGTH, this.typeId(), objectId);

		// store super class' fields AFTER the entity header has been written (and buffer space has been reserved).
		super.internalStore(data, instance, objectId, handler);
		data.storeReference(BINARY_OFFSET_indexer, handler, XMemory.getObject(instance, MEMORY_OFFSET_indexer));
		data.storeReference(BINARY_OFFSET_entry  , handler, XMemory.getObject(instance, MEMORY_OFFSET_entry  ));
	}
	
	@Override
	public SingleBitmapIndex<?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		return new SingleBitmapIndex<>(null, null, null, null, false);
	}
	
	@SuppressWarnings("unchecked")
	private static <E, K> Indexer<? super E, K> getIndexer(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (Indexer<? super E, K>)data.readReference(BINARY_OFFSET_indexer, handler);
	}

	@SuppressWarnings("unchecked")
	private static <E> BitmapEntry<E, E, Boolean> getEntry(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (BitmapEntry<E, E, Boolean>)data.readReference(BINARY_OFFSET_entry, handler);
	}
	
	@Override
	protected <E> void internalUpdateState(
		final Binary                 data    ,
		final SingleBitmapIndex<?>   instance,
		final PersistenceLoadHandler handler
	)
	{
		super.internalUpdateState(data, instance, handler);
				
		// sets the back references to parent and key internally
		instance.setEntry(getEntry(data, handler));
		
		final Indexer<? super E, Boolean> indexer = getIndexer(data, handler);
		XMemory.setObject(instance, MEMORY_OFFSET_indexer, indexer);
	}
	
	@Override
	public void iterateInstanceReferences(final SingleBitmapIndex<?> instance, final PersistenceFunction iterator)
	{
		super.iterateInstanceReferences(instance, iterator);
		iterator.apply(instance.indexer);
		iterator.apply(instance.entry);
	}

	@Override
	public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		super.iterateLoadableReferences(data, iterator);
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_indexer));
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_entry  ));
	}
		
}
