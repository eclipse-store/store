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
import org.eclipse.serializer.persistence.binary.types.BinaryLegacyTypeHandler;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceReferenceLoader;
import org.eclipse.serializer.reference.Lazy;


/**
 * Legacy handler for {@link BitmapEntry} type definitions that declared the
 * {@code level3} field as {@link Lazy} in the stored type dictionary.
 * <p>
 * The historical {@link BinaryHandlerBitmapEntry} declared a {@code CustomField(Lazy.class, "level3")}
 * even though the actual instance field is of type {@link BitmapLevel3} and the handler
 * always wrote/read a plain object reference to a {@link BitmapLevel3}. The on-disk binary
 * layout is therefore identical to the current one; only the declared field type in the
 * type dictionary differs. This handler exists so that storages written by the old
 * declaration are matched cleanly against their stored type definition instead of relying
 * on heuristic legacy mapping.
 */
public class BinaryLegacyTypeHandlerBitmapEntry
extends BinaryLegacyTypeHandler.AbstractCustom<BitmapEntry<?, ?, ?>>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////

	private static final long
		BINARY_OFFSET_level3   = 0                                                   ,
		BINARY_OFFSET_position = BINARY_OFFSET_level3   + Binary.objectIdByteLength(),

		MEMORY_OFFSET_level3   = getClassDeclaredFieldOffset(BinaryHandlerBitmapEntry.genericType(), "level3")
	;



	public static BinaryLegacyTypeHandlerBitmapEntry New()
	{
		return new BinaryLegacyTypeHandlerBitmapEntry();
	}



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	BinaryLegacyTypeHandlerBitmapEntry()
	{
		super(
			BinaryHandlerBitmapEntry.genericType(),
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
	public BitmapEntry<?, ?, ?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		final int position = data.read_int(BINARY_OFFSET_position);

		return new BitmapEntry<>(null, null, position, false);
	}

	@Override
	public void updateState(
		final Binary                 data    ,
		final BitmapEntry<?, ?, ?>   instance,
		final PersistenceLoadHandler handler
	)
	{
		// The stored objectId always referenced a BitmapLevel3 directly, despite the
		// legacy Lazy declaration. Reading it as a plain reference is therefore safe.
		final BitmapLevel3 level3 = (BitmapLevel3)data.readReference(BINARY_OFFSET_level3, handler);
		XMemory.setObject(instance, MEMORY_OFFSET_level3, level3);
	}

	@Override
	public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_level3));
	}

}
