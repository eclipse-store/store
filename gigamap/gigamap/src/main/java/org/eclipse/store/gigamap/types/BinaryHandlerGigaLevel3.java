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
 * BinaryHandlerGigaLevel3 is a binary persistence handler tailored for the {@link GigaLevel3} class.
 * This handler facilitates creation, state updates, persistence operations, and the handling
 * of references for instances of GigaLevel3. The handler also integrates state change tracking
 * capabilities to avoid redundant persistence operations unless the instance state changes.
 * <p>
 * This class extends {@link AbstractBinaryHandlerStateChangeFlagged} and inherits the ability to manage
 * instances of types that implement state change tracking.
 */
public final class BinaryHandlerGigaLevel3 extends AbstractBinaryHandlerStateChangeFlagged<GigaLevel3<?>>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
		
	private static final long BINARY_OFFSET_segments = 0L;
		
	
	
	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////
	
	@SuppressWarnings({"unchecked",  "rawtypes"})
	private static Class<GigaLevel3<?>> handledType()
	{
		// no idea how to get ".class" to work otherwise
		return (Class)GigaLevel3.class;
	}
	
	public static BinaryHandlerGigaLevel3 New()
	{
		return new BinaryHandlerGigaLevel3();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	BinaryHandlerGigaLevel3()
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
	public GigaLevel3<?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		final int arrayLength = X.checkArrayRange(data.getListElementCountReferences(BINARY_OFFSET_segments));
		return new GigaLevel3<>(arrayLength, false);
	}
		
	@Override
	public void updateState(
		final Binary                 data    ,
		final GigaLevel3<?>          instance,
		final PersistenceLoadHandler handler
	)
	{
		// validation already happened before array creation
		data.collectElementsIntoArray(BINARY_OFFSET_segments, handler, instance.segments);
	}
	

	@Override
	public void internalStore(
		final Binary                          data    ,
		final GigaLevel3<?>                   instance,
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
	public final void iterateInstanceReferences(final GigaLevel3<?> instance, final PersistenceFunction iterator)
	{
		Persistence.iterateReferences(iterator, instance.segments, 0, instance.segments.length);
	}

	@Override
	public final void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		data.iterateListElementReferences(BINARY_OFFSET_segments, iterator);
	}
													
}
