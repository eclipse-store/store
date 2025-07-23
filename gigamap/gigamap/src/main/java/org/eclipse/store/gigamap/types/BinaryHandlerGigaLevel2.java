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
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.util.X;


/**
 * BinaryHandlerGigaLevel2 is a persistence handler responsible for managing
 * the binary serialization and deserialization process for {@link GigaLevel2} type objects.
 * It facilitates the storage, retrieval, and state tracking of {@link GigaLevel2} instances
 * while ensuring consistency and proper handling of their internal child references.
 * <p>
 * This class extends {@link AbstractBinaryHandlerStateChangeFlagged}, leveraging
 * its state change tracking and storage mechanisms to efficiently persist objects that have
 * undergone changes or are newly created.
 * <p>
 * Key functionalities of this handler include:
 * <ul>
 * <li>Creating a new {@code GigaLevel2<?>} instance from binary data.</li>
 * <li>Updating the state of an existing {@code GigaLevel2<?>} instance based on binary data.</li>
 * <li>Storing the binary representation of {@code GigaLevel2<?>} objects, including their child references.</li>
 * <li>Iterating through references for persistence purposes, either across instances or binary data.</li>
 * </ul>
 */
public final class BinaryHandlerGigaLevel2 extends AbstractBinaryHandlerStateChangeFlagged<GigaLevel2<?>>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
		
	private static final long BINARY_OFFSET_segments = 0L;
		
	
	
	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////
	
	@SuppressWarnings({"unchecked",  "rawtypes"})
	private static Class<GigaLevel2<?>> handledType()
	{
		// no idea how to get ".class" to work otherwise
		return (Class)GigaLevel2.class;
	}
	
	public static BinaryHandlerGigaLevel2 New()
	{
		return new BinaryHandlerGigaLevel2();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	BinaryHandlerGigaLevel2()
	{
		super(
			handledType(),
			CustomFields(
				Complex("segments",
					CustomField(Lazy.class, "segment")
				)
			)
		);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public GigaLevel2<?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		final int arrayLength = X.checkArrayRange(data.getListElementCountReferences(BINARY_OFFSET_segments));
		return new GigaLevel2<>(arrayLength, false);
	}
		
	@Override
	public void updateState(
		final Binary                 data    ,
		final GigaLevel2<?>          instance,
		final PersistenceLoadHandler handler
	)
	{
		// validation already happened before array creation
		data.collectElementsIntoArray(BINARY_OFFSET_segments, handler, instance.segments);
	}
	

	@Override
	public void internalStore(
		final Binary                          data    ,
		final GigaLevel2<?>                   instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		data.storeReferences(
			this.typeId(),
			objectId,
			BINARY_OFFSET_segments,
			handler,
			instance.segments,
			0,
			instance.segments.length
		);
	}
	
	@Override
	public final void iterateInstanceReferences(final GigaLevel2<?> instance, final PersistenceFunction iterator)
	{
		Persistence.iterateReferences(iterator, instance.segments);
	}

	@Override
	public final void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		data.iterateListElementReferences(BINARY_OFFSET_segments, iterator);
	}
													
}
