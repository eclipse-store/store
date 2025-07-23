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

import org.eclipse.serializer.equality.Equalator;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.AbstractBinaryHandlerCustom;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceReferenceLoader;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;


/**
 * This class provides a custom binary handler implementation for {@link GigaMap.Default}.
 * It is responsible for serializing and deserializing GigaMap.Default instances to and from binary format.
 * It extends {@link AbstractBinaryHandlerCustom} with specific customization for GigaMap.Default handling.
 * <p>
 * The storage and retrieval logic is specifically tailored to optimize the performance
 * and ensure consistency during persistence operations.
 */
public class BinaryHandlerGigaMapDefault extends AbstractBinaryHandlerCustom<GigaMap.Default<?>>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
	
	private static final long
		BINARY_OFFSET_level3               = 0                                                               ,
		BINARY_OFFSET_indices              = BINARY_OFFSET_level3               + Binary.objectIdByteLength(),
		BINARY_OFFSET_constraints          = BINARY_OFFSET_indices              + Binary.objectIdByteLength(),
		BINARY_OFFSET_equalator            = BINARY_OFFSET_constraints          + Binary.objectIdByteLength(),
		BINARY_OFFSET_level1LengthExponent = BINARY_OFFSET_equalator            + Binary.objectIdByteLength(),
		BINARY_OFFSET_level2LengthExponent = BINARY_OFFSET_level1LengthExponent + Integer.BYTES              ,
		BINARY_OFFSET_level3MinLenExponent = BINARY_OFFSET_level2LengthExponent + Integer.BYTES              ,
		BINARY_OFFSET_level3MaxLenExponent = BINARY_OFFSET_level3MinLenExponent + Integer.BYTES              ,
		BINARY_OFFSET_size                 = BINARY_OFFSET_level3MaxLenExponent + Integer.BYTES              ,
		BINARY_OFFSET_currentId            = BINARY_OFFSET_size                 + Long.BYTES                 ,
		BINARY_LENGTH                      = BINARY_OFFSET_currentId            + Long.BYTES                 ,
		
		MEMORY_OFFSET_level3               = getClassDeclaredFieldOffset(genericType(), "level3" ),
		MEMORY_OFFSET_indices              = getClassDeclaredFieldOffset(genericType(), "indices"),
		MEMORY_OFFSET_constraints          = getClassDeclaredFieldOffset(genericType(), "constraints"),
		MEMORY_OFFSET_equalator            = getClassDeclaredFieldOffset(genericType(), "equalator"),
		MEMORY_OFFSET_level1LengthExponent = getClassDeclaredFieldOffset(genericType(), "level1LengthExponent"),
		MEMORY_OFFSET_level2LengthExponent = getClassDeclaredFieldOffset(genericType(), "level2LengthExponent"),
		MEMORY_OFFSET_level3MinLenExponent = getClassDeclaredFieldOffset(genericType(), "level3MinimumLengthExponent"),
		MEMORY_OFFSET_level3MaxLenExponent = getClassDeclaredFieldOffset(genericType(), "level3MaximumLengthExponent"),
		MEMORY_OFFSET_baseSize             = getClassDeclaredFieldOffset(genericType(), "baseSize"),
		MEMORY_OFFSET_baseAddingId         = getClassDeclaredFieldOffset(genericType(), "baseAddingId")
	;
	
	
	@SuppressWarnings("all")
	public static final Class<GigaMap.Default<?>> genericType()
	{
		// no idea how to get ".class" to work otherwise in conjunction with generics.
		return (Class)GigaMap.Default.class;
	}
	
	public static BinaryHandlerGigaMapDefault New()
	{
		return new BinaryHandlerGigaMapDefault();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	BinaryHandlerGigaMapDefault()
	{
		super(
			genericType(),
			CustomFields(
				CustomField(             GigaLevel3.class, "level3"                     ),
				CustomField(    GigaIndices.Default.class, "indices"                    ),
				CustomField(GigaConstraints.Default.class, "constraints"                ),
				CustomField(              Equalator.class, "equalator"                  ),
				CustomField(                    int.class, "level1LengthExponent"       ),
				CustomField(                    int.class, "level2LengthExponent"       ),
				CustomField(                    int.class, "level3MinimumLengthExponent"),
				CustomField(                    int.class, "level3MaximumLengthExponent"),
				CustomField(                   long.class, "size"                       ),
				CustomField(                   long.class, "currentId"                  )
			)
		);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public void store(
		final Binary                          data    ,
		final GigaMap.Default<?>              instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		// this is just a local, partial lock that does NOT protect the whole giga map storing process. See GigaMap#store.
		synchronized(instance)
		{
			// Guarantee store context link. No-op from the second call on.
			instance.linkStoreContext(handler.getPersister());
			
			data.storeEntityHeader(BINARY_LENGTH, this.typeId(), objectId);
			
			// stores referenced instances only if they are new ("lazy"). #registerChangeStores handles "changed and not new".
			data.storeReference(BINARY_OFFSET_level3     , handler, instance.level3()     );
			data.storeReference(BINARY_OFFSET_indices    , handler, instance.index()      );
			data.storeReference(BINARY_OFFSET_constraints, handler, instance.constraints());
			data.storeReference(BINARY_OFFSET_equalator  , handler, instance.equalator());
			
			data.store_int (BINARY_OFFSET_level1LengthExponent, XMemory.get_int(instance, MEMORY_OFFSET_level1LengthExponent));
			data.store_int (BINARY_OFFSET_level2LengthExponent, XMemory.get_int(instance, MEMORY_OFFSET_level2LengthExponent));
			data.store_int (BINARY_OFFSET_level3MinLenExponent, XMemory.get_int(instance, MEMORY_OFFSET_level3MinLenExponent));
			data.store_int (BINARY_OFFSET_level3MaxLenExponent, XMemory.get_int(instance, MEMORY_OFFSET_level3MaxLenExponent));
			data.store_long(BINARY_OFFSET_size                , instance.size()      );
			data.store_long(BINARY_OFFSET_currentId           , instance.nextFreeId());
			
			// registers referenced instances (recursively!) if they are ONLY "changed" (NOT "new and changed")
			instance.internalRegisterChangeStores(handler);
		}
	}
	
	@Override
	public GigaMap.Default<?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		final int level1LengthExponent = data.read_int (BINARY_OFFSET_level1LengthExponent);
		final int level2LengthExponent = data.read_int (BINARY_OFFSET_level2LengthExponent);
		final int level3MinLenExponent = data.read_int (BINARY_OFFSET_level3MinLenExponent);
		final int level3MaxLenExponent = data.read_int (BINARY_OFFSET_level3MaxLenExponent);
		final long size                = data.read_long(BINARY_OFFSET_size                );
		final long currentId           = data.read_long(BINARY_OFFSET_currentId           );
		
		/*
		 * Notes:
		 * 1.)
		 * Instance creation false since they are created and set by the loading logic.
		 * 
		 * 2.)
		 * Setting the full size and currentId as the base~ fields seems wrong, but is actually correct.
		 * Since currentLevel1Index will initially be 0, the calculation is correct (only!) this way.
		 * And #setupCurrent will recalculate the two base~ values depending on what currentLevel1Index will be.
		 * It's all just an arithmetic parlor trick to create a per-segment size updating behavior instead of per #add.
		 */
		return new GigaMap.Default<>(
			null                ,
			level1LengthExponent,
			level2LengthExponent,
			level3MinLenExponent,
			level3MaxLenExponent,
			size                ,
			currentId           ,
			false
		);
	}
	
	@SuppressWarnings("unchecked")
	private static <E> GigaLevel3<E> getLevel3(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (GigaLevel3<E>)data.readReference(BINARY_OFFSET_level3, handler);
	}
	
	private static GigaIndices.Default<?> getIndices(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (GigaIndices.Default<?>)data.readReference(BINARY_OFFSET_indices, handler);
	}
	
	private static GigaConstraints.Default<?> getConstraints(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (GigaConstraints.Default<?>)data.readReference(BINARY_OFFSET_constraints, handler);
	}
	
	private static Equalator<?> getEqualator(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (Equalator<?>)data.readReference(BINARY_OFFSET_equalator, handler);
	}
	
	
	
	@Override
	public void updateState(
		final Binary                 data    ,
		final GigaMap.Default<?>     instance,
		final PersistenceLoadHandler handler
	)
	{
		// instance must be linked to its store context in order to properly store its index structure.
		instance.linkStoreContext(handler.getPersister());
		instance.ensureClearedSetupState();
		
		XMemory.setObject(instance, MEMORY_OFFSET_level3      , getLevel3 (data, handler)    );
		XMemory.setObject(instance, MEMORY_OFFSET_indices     , getIndices(data, handler)    );
		XMemory.setObject(instance, MEMORY_OFFSET_constraints , getConstraints(data, handler));
		XMemory.setObject(instance, MEMORY_OFFSET_equalator   , getEqualator(data, handler)  );
		
		// Has to be set again, in case of just updating an instance
		final long size      = data.read_long(BINARY_OFFSET_size);
		final long currentId = data.read_long(BINARY_OFFSET_currentId);
		XMemory.set_long(instance, MEMORY_OFFSET_baseSize    , size     );
		XMemory.set_long(instance, MEMORY_OFFSET_baseAddingId, currentId);
	}
	
	@Override
	public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_level3));
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_indices));
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_constraints));
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_equalator));
	}
		
}
