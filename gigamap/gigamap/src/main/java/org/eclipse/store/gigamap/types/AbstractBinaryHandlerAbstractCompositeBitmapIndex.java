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
import org.eclipse.serializer.persistence.types.*;
import org.eclipse.serializer.util.X;


/**
 * Represents an abstract binary handler designed for handling instances of {@link AbstractCompositeBitmapIndex}.
 * This class extends the functionality provided by {@link AbstractBinaryHandlerBitmapIndexAbstract}
 * to manage composite bitmap indices and their associated binary serialization and deserialization behavior.
 *
 * @param <C> specifies the concrete subclass of {@link AbstractCompositeBitmapIndex} handled by this class.
 * @param <KS> represents the type used for the key set in the composite index.
 * @param <K> represents the type used for the keys in the composite index.
 */
public abstract class AbstractBinaryHandlerAbstractCompositeBitmapIndex<C extends AbstractCompositeBitmapIndex<?, KS, K>, KS, K>
extends AbstractBinaryHandlerBitmapIndexAbstract<C>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
	
	private static final long
		BINARY_OFFSET_indexer    = binaryOffsetSubClassFields()                              ,
		BINARY_OFFSET_subIndices = BINARY_OFFSET_indexer        + Binary.objectIdByteLength(),
		
		MEMORY_OFFSET_indexer    = getClassDeclaredFieldOffset(AbstractCompositeBitmapIndex.class, "indexer"   ),
		MEMORY_OFFSET_subIndices = getClassDeclaredFieldOffset(AbstractCompositeBitmapIndex.class, "subIndices")
	;
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	protected AbstractBinaryHandlerAbstractCompositeBitmapIndex(final Class<C> type)
	{
		super(
			type,
			CustomFields(
				CustomField(CompositeIndexer.class, "indexer"),
				Complex("subIndices",
					CustomField(AbstractCompositeBitmapIndex.Sub.class, "subIndex")
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
		final C                               instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		final AbstractCompositeBitmapIndex.Sub<?, KS, ?>[] subIndices = instance.subIndices();
		data.storeReferences(this.typeId(), objectId, BINARY_OFFSET_subIndices, handler, subIndices);
		
		// store super class' fields AFTER the entity header has been written (and buffer space has been reserved).
		super.internalStore(data, instance, objectId, handler);
		data.storeReference(BINARY_OFFSET_indexer, handler, XMemory.getObject(instance, MEMORY_OFFSET_indexer));
	}
	
	@SuppressWarnings("unchecked")
	private static <E, KS> CompositeIndexer<? super E, KS> getIndexer(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (CompositeIndexer<? super E, KS>)data.readReference(BINARY_OFFSET_indexer, handler);
	}
	
	protected <E> AbstractCompositeBitmapIndex.Sub<E, KS, K>[] createSubIndicesArray(final int arrayLength)
	{
		return AbstractCompositeBitmapIndex.createSubIndicesArray(arrayLength);
	}
	
	@Override
	protected <E> void internalUpdateState(
		final Binary                 data    ,
		final C                      instance,
		final PersistenceLoadHandler handler
	)
	{
		super.internalUpdateState(data, instance, handler);
		
		final int                                          arrayLength = X.checkArrayRange(data.getListElementCountReferences(BINARY_OFFSET_subIndices));
		final AbstractCompositeBitmapIndex.Sub<E, KS, ?>[] array       = this.createSubIndicesArray(arrayLength);
		data.collectElementsIntoArray(BINARY_OFFSET_subIndices, handler, array);

		for(int i = 0; i <arrayLength; i++)
		{
			array[i].setPosition(i);
		}
		
		final CompositeIndexer<? super E, KS> indexer = getIndexer(data, handler);
		XMemory.setObject(instance, MEMORY_OFFSET_indexer   , indexer);
		XMemory.setObject(instance, MEMORY_OFFSET_subIndices, array);
		
		instance.initializeTransientState();
	}
	
	@Override
	public void iterateInstanceReferences(final C instance, final PersistenceFunction iterator)
	{
		iterator.apply(instance.parent);
		iterator.apply(instance.name);
		iterator.apply(instance.indexer);
		Persistence.iterateReferences(iterator, instance.subIndices);
	}

	@Override
	public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		super.iterateLoadableReferences(data, iterator);
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_indexer));
		data.iterateListElementReferences(BINARY_OFFSET_subIndices, iterator);
	}
		
}
