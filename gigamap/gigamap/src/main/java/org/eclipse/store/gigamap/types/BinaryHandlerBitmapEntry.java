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
import org.eclipse.serializer.reference.Lazy;


/**
 * BinaryHandlerBitmapEntry provides a persistence handler for managing binary
 * storage and retrieval operations for {@link BitmapEntry} instances. This
 * class leverages {@link AbstractBinaryHandlerStateChangeFlagged} to support
 * efficient storage operations by handling instances only if they are new or
 * changed.
 * <p>
 * The {@link BitmapEntry} instances are represented in binary form with
 * specific offsets and lengths defined for their fields. The class ensures the
 * correct mapping of object properties, including references, to binary
 * formats and vice versa.
 * <p>
 * Primary Responsibilities:
 * <ul>
 * <li>Defines how {@link BitmapEntry} is serialized into binary format.</li>
 * <li>Defines how {@link BitmapEntry} is deserialized from binary format.</li>
 * <li>Handles object references during both serialization and deserialization.</li>
 * <li>Facilitates iteration through instance and binary-stored references.</li>
 * </ul>
 * This implementation is particularly tailored for handling {@link BitmapEntry}
 * objects with specific fields and other related components, such as levels
 * and positions.
 */
public class BinaryHandlerBitmapEntry extends AbstractBinaryHandlerStateChangeFlagged<BitmapEntry<?, ?, ?>>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
	
	private static final long
		BINARY_OFFSET_level3   = 0                                                   ,
		BINARY_OFFSET_position = BINARY_OFFSET_level3   + Binary.objectIdByteLength(),
		BINARY_LENGTH          = BINARY_OFFSET_position + Integer.BYTES,
		
		MEMORY_OFFSET_level3   = getClassDeclaredFieldOffset(genericType(), "level3")
	;
	
	@SuppressWarnings("all")
	public static final Class<BitmapEntry<?, ?, ?>> genericType()
	{
		// no idea how to get ".class" to work otherwise in conjunction with generics.
		return (Class)BitmapEntry.class;
	}
	
	
	
	public static BinaryHandlerBitmapEntry New()
	{
		return new BinaryHandlerBitmapEntry();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	BinaryHandlerBitmapEntry()
	{
		super(
			genericType(),
			CustomFields(
				CustomField(Lazy.class, "level3"  ),
				CustomField(int.class , "position")
			)
		);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public void internalStore(
		final Binary                          data    ,
		final BitmapEntry<?, ?, ?>            instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		data.storeEntityHeader(BINARY_LENGTH, this.typeId(), objectId);
		data.storeReference   (BINARY_OFFSET_level3  , handler, XMemory.getObject(instance, MEMORY_OFFSET_level3));
		data.store_int        (BINARY_OFFSET_position, instance.position());
	}
		
	@Override
	public BitmapEntry<?, ?, ?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		final int position = data.read_int(BINARY_OFFSET_position);
		
		return new BitmapEntry<>(null, null, position, false);
	}
	
	private static BitmapLevel3 getLevel3(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (BitmapLevel3)data.readReference(BINARY_OFFSET_level3, handler);
	}

			 
	
	@Override
	public void updateState(
		final Binary                 data    ,
		final BitmapEntry<?, ?, ?>   instance,
		final PersistenceLoadHandler handler
	)
	{
		XMemory.setObject(instance, MEMORY_OFFSET_level3, getLevel3(data, handler));
	}
	
	@Override
	public void iterateInstanceReferences(final BitmapEntry<?, ?, ?> instance, final PersistenceFunction iterator)
	{
		iterator.apply(instance.level3);
	}

	@Override
	public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_level3));
	}
		
}
