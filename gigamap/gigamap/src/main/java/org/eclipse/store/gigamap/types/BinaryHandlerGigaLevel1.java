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

import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.*;
import org.eclipse.serializer.util.X;


/**
 * The BinaryHandlerGigaLevel1 class is a concrete implementation of {@link AbstractBinaryHandlerStateChangeFlagged},
 * specifically designed to handle the binary persistence and state management for {@link GigaLevel1} instances.
 * <p>
 * This class provides methods for creating, updating, storing, and iterating through references of objects
 * of type {@link GigaLevel1} with binary data. It also ensures proper handling of the object's state change
 * flags to improve efficiency in persistence operations.
 * <p>
 * Key responsibilities:
 * <ul>
 * <li>Creating new GigaLevel1<?> instances from binary data.</li>
 * <li>Updating the state of GigaLevel1<?> instances from binary data.</li>
 * <li>Storing GigaLevel1<?> instances into binary format, managing reference lists as needed.</li>
 * <li>Navigating and iterating the references of GigaLevel1<?> objects for persistence operations.</li>
 * </ul>
 */
public final class BinaryHandlerGigaLevel1 extends AbstractBinaryHandlerStateChangeFlagged<GigaLevel1<?>>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
		
	private static final long BINARY_OFFSET_segments = 0L;
		
	
	
	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////
	
	@SuppressWarnings({"unchecked",  "rawtypes"})
	private static Class<GigaLevel1<?>> handledType()
	{
		// no idea how to get ".class" to work otherwise
		return (Class)GigaLevel1.class;
	}
	
	public static BinaryHandlerGigaLevel1 New()
	{
		return new BinaryHandlerGigaLevel1();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	BinaryHandlerGigaLevel1()
	{
		super(
			handledType(),
			CustomFields(
				Complex("entities",
					CustomField(Object.class, "entity")
				)
			)
		);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public GigaLevel1<?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		final int arrayLength = X.checkArrayRange(data.getListElementCountReferences(BINARY_OFFSET_segments));
		return new GigaLevel1<>(arrayLength, false);
	}
		
	@Override
	public void updateState(
		final Binary                 data    ,
		final GigaLevel1<?>          instance,
		final PersistenceLoadHandler handler
	)
	{
		// validation already happened before array creation
		data.collectElementsIntoArray(BINARY_OFFSET_segments, handler, instance.entities);
	}
	

	@Override
	public void internalStore(
		final Binary                          data    ,
		final GigaLevel1<?>                   instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		data.storeReferences(
			this.typeId(),
			objectId,
			BINARY_OFFSET_segments,
			handler,
			instance.entities,
			0,
			instance.entities.length
		);
	}
	
	@Override
	public final void iterateInstanceReferences(final GigaLevel1<?> instance, final PersistenceFunction iterator)
	{
		Persistence.iterateReferences(iterator, instance.entities);
	}

	@Override
	public final void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		data.iterateListElementReferences(BINARY_OFFSET_segments, iterator);
	}
		
												
}
