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
import org.eclipse.serializer.typing.XTypes;
import org.eclipse.serializer.util.X;


/**
 * BinaryHandlerBitmapLevel3 is a concrete implementation of the binary persistence handler
 * for the {@link BitmapLevel3} type. It handles the serialization, deserialization, and persistence of
 * `BitmapLevel3` objects, including managing their internal state and references to child
 * objects of type `BitmapLevel2`.
 * <p>
 * This class extends the {@link AbstractBinaryHandlerStateChangeFlagged} class, leveraging its
 * ability to track state changes for efficient persistence. It provides specific logic for
 * creating, updating, storing, and iterating over instances of {@link BitmapLevel3}.
 */
// TODO check compressed storing
public class BinaryHandlerBitmapLevel3 extends AbstractBinaryHandlerStateChangeFlagged<BitmapLevel3>
{
	public static BinaryHandlerBitmapLevel3 New()
	{
		return new BinaryHandlerBitmapLevel3();
	}
	
	
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
		
	private static final long BINARY_OFFSET_segments = 0L;
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	BinaryHandlerBitmapLevel3()
	{
		super(
			BitmapLevel3.class,
			CustomFields(
				Complex("segments",
					CustomField(BitmapLevel2.class, "segment")
				)
			)
		);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	public BitmapLevel3 create(final Binary data, final PersistenceLoadHandler handler)
	{
		final int length = X.checkArrayRange(data.getListElementCountReferences(BINARY_OFFSET_segments));
		return new BitmapLevel3(length, false);
	}
		
	@Override
	public void updateState(
		final Binary                 data    ,
		final BitmapLevel3           instance,
		final PersistenceLoadHandler handler
	)
	{
		// validation already happened before array creation
		final long segmentCount = data.collectElementsIntoArray(BINARY_OFFSET_segments, handler, instance.segments);
		instance.initializeSegmentCount(XTypes.to_int(segmentCount));
	}
	
	@Override
	public void complete(
		final Binary                 data    ,
		final BitmapLevel3           instance,
		final PersistenceLoadHandler handler
	)
	{
		long totalSegmentCount = 0L;
		for(final BitmapLevel2 e : instance.segments())
		{
			if(e == null)
			{
				continue;
			}
			totalSegmentCount += e.segmentCount();
		}
		instance.initializeTotalSegmentCount(totalSegmentCount);
	}
	
	@Override
	public void internalStore(
		final Binary                          data    ,
		final BitmapLevel3                    instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		final BitmapLevel2[] segments       = instance.segments();
		final int            effArrayLength = determineEffectiveLength(segments);

		// array is stored in full right behind the empty index
		data.storeReferences(
			this.typeId(),
			objectId,
			BINARY_OFFSET_segments,
			handler,
			segments,
			0,
			effArrayLength
		);
	}
	
	private static int determineEffectiveLength(final Object[] array)
	{
		for(int i = array.length; i --> 0;)
		{
			if(array[i] != null)
			{
				return i + 1;
			}
		}
		
		return 0;
	}
	
	@Override
	public void iterateInstanceReferences(final BitmapLevel3 instance, final PersistenceFunction iterator)
	{
		Persistence.iterateReferences(iterator, instance.segments(), 0, instance.localSegmentCount());
	}
	
	@Override
	public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		data.iterateListElementReferences(BINARY_OFFSET_segments, iterator);
	}
	
}
