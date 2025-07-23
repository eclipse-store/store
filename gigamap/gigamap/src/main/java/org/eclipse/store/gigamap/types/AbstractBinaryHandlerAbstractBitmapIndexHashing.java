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
import org.eclipse.serializer.collections.KeyValueFlatCollector;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.*;
import org.eclipse.serializer.util.X;


/**
 * Provides an abstract implementation for handling binary data storage and retrieval
 * mechanisms for a specific type of binary-based bitmap index with abstract hashing.
 * This class is primarily designed to support efficient persistence and reconstruction
 * of indexing data structures.
 *
 * @param <I> The type of the bitmap index being handled, extending AbstractBitmapIndexHashing.
 * @param <K> The type of key used within the bitmap index.
 */
public abstract class AbstractBinaryHandlerAbstractBitmapIndexHashing<I extends AbstractBitmapIndexHashing<?, ?, ?>, K>
extends AbstractBinaryHandlerBitmapIndexAbstract<I>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
	
	private static final long
		BINARY_OFFSET_indexer = binaryOffsetSubClassFields()                       ,
		BINARY_OFFSET_entries = BINARY_OFFSET_indexer + Binary.objectIdByteLength(),
	
		MEMORY_OFFSET_indexer = getClassDeclaredFieldOffset(AbstractBitmapIndexHashing.class, "indexer"),
		MEMORY_OFFSET_entries = getClassDeclaredFieldOffset(AbstractBitmapIndexHashing.class, "entries")
	;
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	protected AbstractBinaryHandlerAbstractBitmapIndexHashing(final Class<I> type)
	{
		super(
			type,
			CustomFields(
				CustomField(Indexer.class, "indexer"),
				Complex("entries",
					CustomField(Object.class, "key"),
					CustomField(BitmapEntry.class, "entry")
				)
			)
		);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	protected void internalStore(
		final Binary                          data    ,
		final I                               instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		data.storeKeyValuesAsEntries(
			this.typeId(),
			objectId,
			BINARY_OFFSET_entries,
			instance.entries(),
			instance.entries().intSize(),
			handler
		);
		
		// store super class' fields AFTER the entity header has been written (and buffer space has been reserved).
		super.internalStore(data, instance, objectId, handler);
		data.storeReference(BINARY_OFFSET_indexer, handler, XMemory.getObject(instance, MEMORY_OFFSET_indexer));
	}
	
	@Override
	public abstract I create(final Binary data, final PersistenceLoadHandler handler);
	
	@SuppressWarnings("unchecked")
	private static <E, K> Indexer<? super E, K> getIndexer(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (Indexer<? super E, K>)data.readReference(BINARY_OFFSET_indexer, handler);
	}
	
	@Override
	protected <E> void internalUpdateState(
		final Binary                 data    ,
		final I                      instance,
		final PersistenceLoadHandler handler
	)
	{
		super.internalUpdateState(data, instance, handler);
		
		final Indexer<? super E, K>                indexer = getIndexer(data, handler);
		final int                                  elementCount = X.checkArrayRange(data.getListElementCountKeyValue(BINARY_OFFSET_entries));
		final EqHashTable<K, BitmapEntry<?, ?, ?>> entries = EqHashTable.New(indexer.hashEqualator());
		
		XMemory.setObject(instance, MEMORY_OFFSET_indexer, indexer);
		XMemory.setObject(instance, MEMORY_OFFSET_entries, entries);
		
		final KeyValueFlatCollector<Object, Object> collector = KeyValueFlatCollector.New(elementCount);
		data.collectKeyValueReferences(BINARY_OFFSET_entries, elementCount, handler, collector);
		data.registerHelper(instance, collector.yield());
	}
	
	@Override
	public void complete(
		final Binary                 data    ,
		final I                      instance,
		final PersistenceLoadHandler handler
	)
	{
		final Object[] elements = (Object[])data.getHelper(instance);
		for(int i = 0; i < elements.length; i += 2)
		{
			this.addEntry(instance, instance.entries, elements[i], elements[i + 1]);
		}
	}
	
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void addEntry(
		final I           instance,
		final EqHashTable entries ,
		final Object      k       ,
		final Object      v
	)
	{
		final BitmapEntry entry  = (BitmapEntry)v;
		entry.internalSetBackReferences(instance, k);
		entries.add(k, entry);
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
		data.iterateKeyValueEntriesReferences(BINARY_OFFSET_entries, iterator);
	}
	
}
