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
import org.eclipse.serializer.persistence.binary.types.AbstractBinaryHandlerCustom;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.binary.types.BinaryLegacyTypeHandlerSupplier;
import org.eclipse.serializer.persistence.types.PersistenceLegacyTypeHandler;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceReferenceLoader;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;
import org.eclipse.serializer.typing.XTypes;


/**
 * BinaryHandlerBitmapLevel2 is responsible for managing serialization, deserialization,
 * and state updates for objects of type {@link BitmapLevel2} within a persistence framework.
 * It extends {@link AbstractBinaryHandlerCustom} to support binary-based I/O operations
 * specific to {@link BitmapLevel2}.
 */
public class BinaryHandlerBitmapLevel2 extends AbstractBinaryHandlerCustom<BitmapLevel2> implements BinaryLegacyTypeHandlerSupplier<BitmapLevel2> 
{	
	private static final int  BITMAP_LEVEL2_VERSION_SIZE = Integer.BYTES;
	private static final long BITMAP_LIST_OFFSET = BITMAP_LEVEL2_VERSION_SIZE;
	private static final int  BITMAP_LEVEL2_BINARY_VERSION = 2;

	public static BinaryHandlerBitmapLevel2 New()
	{
		return new BinaryHandlerBitmapLevel2();
	}
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	BinaryHandlerBitmapLevel2()
	{
		super(
			BitmapLevel2.class,
			CustomFields(
				CustomField(int.class, "version"),
				bytes("data")
			)
		);
	}
		
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
			
	@Override
	public BitmapLevel2 create(final Binary data, final PersistenceLoadHandler handler)
	{
		final long contentLength = Binary.toBinaryListContentByteLength(data.getBinaryListTotalByteLength(BITMAP_LEVEL2_VERSION_SIZE));
		final int  fullLength    = BitmapLevel2.getTotalLengthFromPersistentLength(XTypes.to_int(contentLength));
		
		final long level2Address     = XMemory.allocate(fullLength);
		final long persistentAddress = BitmapLevel2.toPersistentDataAddress(level2Address);
		data.copyToAddress(Binary.toBinaryListElementsOffset(BITMAP_LIST_OFFSET), persistentAddress, contentLength);
		BitmapLevel2.initializeFromData(level2Address, fullLength);
		
		// required to prevent JVM crashes caused by misinterpreted off-heap data.
		BitmapLevel2.validateLevel2SegmentType(level2Address);
				
		return new BitmapLevel2(false, level2Address);
	}
	
	@Override
	public void store(
		final Binary                          data    ,
		final BitmapLevel2                    instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		// if the parent level3 segment decided to store a level2 segment, it must be stored in any case.
		
		instance.ensureCompressed();
		final long persistentAddress = BitmapLevel2.toPersistentDataAddress(instance.level2Address);
		final int  persistentLength  = BitmapLevel2.getPersistentLengthFromTotalLength(instance.totalLength());
		data.storeEntityHeader(
				Binary.toBinaryListTotalByteLength(persistentLength) + BITMAP_LEVEL2_VERSION_SIZE,
			this.typeId(),
			objectId
		);
		data.store_int(BITMAP_LEVEL2_BINARY_VERSION);
		data.storeListHeader(BITMAP_LIST_OFFSET, persistentLength, persistentLength);
		data.copyFromAddress(Binary.toBinaryListElementsOffset(BITMAP_LIST_OFFSET), persistentAddress, persistentLength);
	}
	
	@Override
	public void updateState(
		final Binary                 data    ,
		final BitmapLevel2           instance,
		final PersistenceLoadHandler handler
	)
	{
		// there are no references to be set, hence nothing to do here
	}
	
	@Override
	public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		// no-op
	}

	@Override
	public PersistenceLegacyTypeHandler<Binary, BitmapLevel2> getLegacyTypeHandler()
	{
		return BinaryLegacyTypeHandlerBitmapLevel2.New();
	}
	
}
