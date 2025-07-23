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
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.*;


/**
 * BinaryHandlerGigaIndicesDefault is a concrete implementation of the {@link AbstractBinaryHandlerStateChangeFlagged}
 * class that handles binary persistence operations for the {@link GigaIndices.Default} type. It facilitates the
 * process of storing, creating, updating, and iterating the references of GigaIndices.Default instances in
 * a binary persistence context.
 * <p>
 * This class is responsible for managing binary offsets, memory offsets, and associating custom fields
 * with the GigaIndices.Default type. It relies on the state-change tracking functionality inherited from
 * its superclass to only store instances that are new or have changed.
 * <p>
 * Key features include:
 * <ul>
 * <li>Storing GigaIndices.Default along with its parent and index group references into a binary representation.</li>
 * <li>Creating a minimal GigaIndices.Default instance representation from binary data.</li>
 * <li>Loading object references (e.g., parents and index groups) and updating the state of the GigaIndices.Default instance.</li>
 * <li>Iterating over instance references and loadable references for persistence operations.</li>
 * </ul>
 */
public class BinaryHandlerGigaIndicesDefault extends AbstractBinaryHandlerStateChangeFlagged<GigaIndices.Default<?>>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
	
	private static final long
		BINARY_OFFSET_parent      =                                                  0,
		BINARY_OFFSET_indexGroups = BINARY_OFFSET_parent + Binary.objectIdByteLength(),
		
		MEMORY_OFFSET_parent      = getClassDeclaredFieldOffset(genericType(), "parent"     ),
		MEMORY_OFFSET_indexGroups = getClassDeclaredFieldOffset(genericType(), "indexGroups")
	;
	
	@SuppressWarnings("all")
	public static final Class<GigaIndices.Default<?>> genericType()
	{
		// no idea how to get ".class" to work otherwise in conjunction with generics.
		return (Class)GigaIndices.Default.class;
	}
	
	
	
	public static BinaryHandlerGigaIndicesDefault New()
	{
		return new BinaryHandlerGigaIndicesDefault();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	BinaryHandlerGigaIndicesDefault()
	{
		super(
			genericType(),
			CustomFields(
				CustomField(GigaMap.class, "parent"),
				Complex("indexGroups",
					CustomField(IndexGroup.class, "indexGroup")
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
		final GigaIndices.Default<?>          instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		final BulkList<IndexGroup<?>> indexGroups = getIndexGroups(instance);
		
		// store elements simply as array binary form
		data.storeIterableAsList(
			this.typeId(),
			objectId,
			BINARY_OFFSET_indexGroups,
			indexGroups,
			indexGroups.size(),
			handler
		);
		
		data.store_long(
			BINARY_OFFSET_parent,
			handler.apply(XMemory.getObject(instance, MEMORY_OFFSET_parent))
		);
	}
	
	@Override
	public GigaIndices.Default<?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		return new GigaIndices.Default<>(null, BulkList.New(), false);
	}
	
	private static GigaMap.Default<?> getParent(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (GigaMap.Default<?>)data.readReference(BINARY_OFFSET_parent, handler);
	}
		
	@SuppressWarnings("unchecked")
	private static BulkList<IndexGroup<?>> getIndexGroups(
		final GigaIndices.Default<?> instance
	)
	{
		return (BulkList<IndexGroup<?>>)XMemory.getObject(instance, MEMORY_OFFSET_indexGroups);
	}
	 
	
	@Override
	public void updateState(
		final Binary                 data    ,
		final GigaIndices.Default<?> instance,
		final PersistenceLoadHandler handler
	)
	{
		XMemory.setObject(instance, MEMORY_OFFSET_parent, getParent(data, handler));

		final BulkList<IndexGroup<?>> indexGroups = getIndexGroups(instance);
		data.iterateListElementReferences(BINARY_OFFSET_indexGroups, oid ->
			indexGroups.add((IndexGroup<?>)handler.lookupObject(oid))
		);
	}
	
	@Override
	public void iterateInstanceReferences(final GigaIndices.Default<?> instance, final PersistenceFunction iterator)
	{
		iterator.apply(instance.parent);
		Persistence.iterateReferencesIterable(iterator, instance.indexGroups);
	}

	@Override
	public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_parent));
		data.iterateListElementReferences(BINARY_OFFSET_indexGroups, iterator);
	}
		
}
