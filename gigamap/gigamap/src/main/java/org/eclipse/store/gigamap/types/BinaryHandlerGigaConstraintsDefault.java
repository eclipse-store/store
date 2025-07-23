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
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceReferenceLoader;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;


/**
 * BinaryHandlerGigaConstraintsDefault is a concrete implementation of
 * {@link AbstractBinaryHandlerStateChangeFlagged} for handling binary persistence
 * of the {@link GigaConstraints.Default} type. This class facilitates the storage,
 * loading, and state update of GigaConstraints.Default instances while leveraging
 * the state-change tracking mechanism for efficient persistence.
 * <p>
 * This handler manages two primary custom fields stored in binary: uniqueConstraints
 * and customConstraints. These fields are serialized and deserialized using the binary
 * offset-based mechanism for optimized access.
 * <p>
 * Key responsibilities include:
 * <ul>
 * <li>Storing instance data into a binary format, utilizing custom offsets for its fields.</li>
 * <li>Loading and creating new GigaConstraints.Default instances from binary data.</li>
 * <li>Updating the state of an instance with data from binary.</li>
 * <li>Iterating over loadable references to ensure child objects are properly resolved.</li>
 * </ul>
 */
public class BinaryHandlerGigaConstraintsDefault extends AbstractBinaryHandlerStateChangeFlagged<GigaConstraints.Default<?>>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
	
	private static final long
		BINARY_OFFSET_uniqueConstraints =                                                             0,
		BINARY_OFFSET_customConstraints = BINARY_OFFSET_uniqueConstraints + Binary.objectIdByteLength(),
		BINARY_LENGTH                   = BINARY_OFFSET_customConstraints + Binary.objectIdByteLength(),

		MEMORY_OFFSET_uniqueConstraints = getClassDeclaredFieldOffset(genericType(), "uniqueConstraints"),
		MEMORY_OFFSET_customConstraints = getClassDeclaredFieldOffset(genericType(), "customConstraints")
	;
	
	@SuppressWarnings("all")
	public static final Class<GigaConstraints.Default<?>> genericType()
	{
		// no idea how to get ".class" to work otherwise in conjunction with generics.
		return (Class)GigaConstraints.Default.class;
	}
	
	public static BinaryHandlerGigaConstraintsDefault New()
	{
		return new BinaryHandlerGigaConstraintsDefault();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	BinaryHandlerGigaConstraintsDefault()
	{
		super(
			genericType(),
			CustomFields(
				// no parent field since BitmapIndices already knows the parent map.
				CustomField(BitmapIndices.class            , "uniqueConstraints"),
				CustomField(CustomConstraints.Default.class, "customConstraints")
			)
		);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
			
	@Override
	protected void internalStore(
		final Binary                          data    ,
		final GigaConstraints.Default<?>      instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		data.storeEntityHeader(BINARY_LENGTH, this.typeId(), objectId);
		
		data.store_long(
			BINARY_OFFSET_uniqueConstraints,
			handler.apply(XMemory.getObject(instance, MEMORY_OFFSET_uniqueConstraints))
		);
		
		data.store_long(
			BINARY_OFFSET_customConstraints,
			handler.apply(XMemory.getObject(instance, MEMORY_OFFSET_customConstraints))
		);
	}
	
	@Override
	public GigaConstraints.Default<?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		return new GigaConstraints.Default<>(null, null, false);
	}
	
	private static BitmapIndices<?> getUniqueConstraints(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (BitmapIndices<?>)data.readReference(BINARY_OFFSET_uniqueConstraints, handler);
	}
		
	private static CustomConstraints.Default<?> getCustomConstraints(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (CustomConstraints.Default<?>)data.readReference(BINARY_OFFSET_customConstraints, handler);
	}
	
	@Override
	public void updateState(
		final Binary                     data    ,
		final GigaConstraints.Default<?> instance,
		final PersistenceLoadHandler     handler
	)
	{
		XMemory.setObject(instance, MEMORY_OFFSET_uniqueConstraints, getUniqueConstraints(data, handler));
		XMemory.setObject(instance, MEMORY_OFFSET_customConstraints, getCustomConstraints(data, handler));
	}

	@Override
	public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_uniqueConstraints));
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_customConstraints));
	}
		
}
