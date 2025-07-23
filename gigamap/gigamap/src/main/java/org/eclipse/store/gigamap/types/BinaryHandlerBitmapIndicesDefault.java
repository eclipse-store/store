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
import org.eclipse.serializer.collections.types.XImmutableEnum;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.*;


/**
 * The BinaryHandlerBitmapIndicesDefault class provides a binary persistence handler
 * for managing instances of {@link BitmapIndices.Default}. This handler works in conjunction
 * with the AbstractBinaryHandlerStateChangeFlagged base class to store and retrieve
 * instances of BitmapIndices.Default in a serialized binary format, while ensuring
 * state change tracking and efficient persistence operations.
 * <p>
 * The class defines constants and offsets for managing the binary layout of the fields
 * associated with a {@link BitmapIndices.Default} instance, and provides methods for storing,
 * loading, and updating object state. It handles references to parent objects, bitmap
 * indices, identity indices, and unique constraints, while enabling the iteration
 * over these references during persistence operations.
 * <p>
 * Key responsibilities of this class include:
 * <ul>
 * <li>Serializing and deserializing the object fields into or from binary data.</li>
 * <li>Managing references to associated objects during persistence operations.</li>
 * <li>Handling eager loading of certain fields to ensure they are populated during runtime.</li>
 * <li>Updating the state of loaded objects and rebuilding caches as necessary.</li>
 * <li>Iterating over references for store or load operations, facilitating efficient
 *   management of related objects in persistence layers.</li>
 * </ul>
 */
public class BinaryHandlerBitmapIndicesDefault extends AbstractBinaryHandlerStateChangeFlagged<BitmapIndices.Default<?>>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
	
	private static final long
		BINARY_OFFSET_parent            =                                                             0,
		BINARY_OFFSET_bitmapIndices     = BINARY_OFFSET_parent            + Binary.objectIdByteLength(),
		BINARY_OFFSET_identityIndices   = BINARY_OFFSET_bitmapIndices     + Binary.objectIdByteLength(),
		BINARY_OFFSET_uniqueConstraints = BINARY_OFFSET_identityIndices   + Binary.objectIdByteLength(),
		BINARY_LENGTH                   = BINARY_OFFSET_uniqueConstraints + Binary.objectIdByteLength(),
	
		MEMORY_OFFSET_bitmapIndices     = getClassDeclaredFieldOffset(genericType(), "bitmapIndices"),
		MEMORY_OFFSET_parent            = getClassDeclaredFieldOffset(genericType(), "parent")
	;
	
	@SuppressWarnings("all")
	public static final Class<BitmapIndices.Default<?>> genericType()
	{
		// no idea how to get ".class" to work otherwise in conjunction with generics.
		return (Class)BitmapIndices.Default.class;
	}
	
	
	
	public static BinaryHandlerBitmapIndicesDefault New()
	{
		return new BinaryHandlerBitmapIndicesDefault();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	BinaryHandlerBitmapIndicesDefault()
	{
		super(
			genericType(),
			CustomFields(
				CustomField(GigaMap.class       , "parent"           ),
				CustomField(EqHashTable.class   , "bitmapIndices"    ),
				CustomField(XImmutableEnum.class, "identityIndices"  ),
				CustomField(XImmutableEnum.class, "uniqueConstraints")
			)
		);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	protected void internalStore(
		final Binary                          data    ,
		final BitmapIndices.Default<?>        instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		data.storeEntityHeader(BINARY_LENGTH, this.typeId(), objectId);
		data.storeReference   (BINARY_OFFSET_parent, handler, instance.parentMap());
		
		// tricky: both indices collections must be stored eagerly to cover newly added indices
		data.storeReferenceEager(BINARY_OFFSET_bitmapIndices    , handler, instance.bitmapIndices()    );
		data.storeReferenceEager(BINARY_OFFSET_identityIndices  , handler, instance.identityIndices()  );
		data.storeReferenceEager(BINARY_OFFSET_uniqueConstraints, handler, instance.uniqueConstraints());
	}
	
	@Override
	public BitmapIndices.Default<?> create(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return new BitmapIndices.Default<>(null, null, false);
	}
	
	private static GigaMap<?> getParent(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (GigaMap<?>)data.readReference(BINARY_OFFSET_parent, handler);
	}
	
	@SuppressWarnings("unchecked")
	private static EqHashTable<String, BitmapIndex.Internal<?, ?>> getBitmapIndices(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (EqHashTable<String, BitmapIndex.Internal<?, ?>>)data.readReference(BINARY_OFFSET_bitmapIndices, handler);
	}
	
	@SuppressWarnings("unchecked")
	private static <E> XImmutableEnum<? extends BitmapIndex<E, ?>> getIdentityIndices(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (XImmutableEnum<? extends BitmapIndex<E, ?>>)data.readReference(BINARY_OFFSET_identityIndices, handler);
	}
	
	@SuppressWarnings("unchecked")
	private static <E> XImmutableEnum<BitmapIndex.Internal<E, ?>> getUniqueConstraints(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (XImmutableEnum<BitmapIndex.Internal<E, ?>>)data.readReference(BINARY_OFFSET_uniqueConstraints, handler);
	}
	
	@Override
	public void updateState(
		final Binary                   data    ,
		final BitmapIndices.Default<?> instance,
		final PersistenceLoadHandler   handler
	)
	{
		// type erasure 4tl.
		this.internalUpdateState(data, instance, handler);
	}
	
	protected <E> void internalUpdateState(
		final Binary                   data    ,
		final BitmapIndices.Default<E> instance,
		final PersistenceLoadHandler   handler
	)
	{
		// setting the two final fields must be done directly, the optional field can be set by methods.
		XMemory.setObject(instance, MEMORY_OFFSET_parent, getParent(data, handler));
		XMemory.setObject(instance, MEMORY_OFFSET_bitmapIndices, getBitmapIndices(data, handler));
		instance.internalSetIdentityIndices(getIdentityIndices(data, handler));
		instance.internalSetUniqueConstraints(getUniqueConstraints(data, handler));
	}
	
	@Override
	public void complete(final Binary data, final BitmapIndices.Default<?> instance, final PersistenceLoadHandler handler)
	{
		instance.rebuildCache();
	}
	
	@Override
	public void iterateInstanceReferences(final BitmapIndices.Default<?> instance, final PersistenceFunction iterator)
	{
		iterator.apply(instance.parent);
		Persistence.iterateReferences(iterator, instance.bitmapIndices);
		Persistence.iterateReferences(iterator, instance.identityIndices);
		Persistence.iterateReferences(iterator, instance.uniqueConstraints);
	}

	@Override
	public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_parent         ));
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_bitmapIndices  ));
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_identityIndices));
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_uniqueConstraints));
	}
		
}
