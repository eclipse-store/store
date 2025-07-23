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

import org.eclipse.serializer.collections.types.XGettingCollection;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.*;
import org.eclipse.serializer.util.X;


/**
 * AbstractBinaryHandlerBitmapIndexAbstract provides an abstract base class for managing the
 * serialization, deserialization, and state management of {@link BitmapIndex.Abstract} instances.
 * This class handles common logic for storing and updating references and manages offsets
 * for binary data formats.
 *
 * @param <I> The type of BitmapIndex.Abstract being handled.
 */
public abstract class AbstractBinaryHandlerBitmapIndexAbstract<I extends BitmapIndex.Abstract<?, ?, ?>>
extends AbstractBinaryHandlerStateChangeFlagged<I>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
	
	private static final long
		BINARY_OFFSET_parent = 0                                                 ,
		BINARY_OFFSET_name   = BINARY_OFFSET_parent + Binary.objectIdByteLength(),
		BINARY_OFFSET_FIELDS = BINARY_OFFSET_name   + Binary.objectIdByteLength(),
		                     
		MEMORY_OFFSET_parent = getClassDeclaredFieldOffset(BitmapIndex.Abstract.class, "parent"),
		MEMORY_OFFSET_name   = getClassDeclaredFieldOffset(BitmapIndex.Abstract.class, "name"  )
	;
	
	protected static long binaryOffsetSubClassFields()
	{
		return BINARY_OFFSET_FIELDS;
	}
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	protected AbstractBinaryHandlerBitmapIndexAbstract(
		final Class<I>                                                                  type          ,
		final XGettingCollection<? extends PersistenceTypeDefinitionMemberFieldGeneric> specificFields
	)
	{
		super(
			type,
			CustomFields(
				X.array(
					CustomField(BitmapIndices.class, "parent"),
					CustomField(String.class       , "name"  )
				),
				specificFields
			)
		);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	protected void internalStore(
		final Binary                          data    ,
		final I                               instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		// no entity header here! The specific classes must store the header with the exact content length and THEN call this method.
		data.storeReference(BINARY_OFFSET_parent, handler, XMemory.getObject(instance, MEMORY_OFFSET_parent));
		data.storeReference(BINARY_OFFSET_name  , handler, XMemory.getObject(instance, MEMORY_OFFSET_name  ));
	}
	
	@Override
	public abstract I create(final Binary data, final PersistenceLoadHandler handler);
	
	protected static BitmapIndices.Default<?> getParent(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (BitmapIndices.Default<?>)data.readReference(BINARY_OFFSET_parent, handler);
	}
	
	protected static String getName(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (String)data.readReference(BINARY_OFFSET_name, handler);
	}
		
	@Override
	public final void updateState(
		final Binary                 data    ,
		final I                      instance,
		final PersistenceLoadHandler handler
	)
	{
		this.internalUpdateState(data, instance, handler);
	}
	
	// <E> is required by subclasses but must be declared here
	protected <E> void internalUpdateState(
		final Binary                 data    ,
		final I                      instance,
		final PersistenceLoadHandler handler
	)
	{
		XMemory.setObject(instance, MEMORY_OFFSET_parent, getParent(data, handler));
		XMemory.setObject(instance, MEMORY_OFFSET_name  , getName  (data, handler));
	}
	
	@Override
	public void iterateInstanceReferences(final I instance, final PersistenceFunction iterator)
	{
		iterator.apply(instance.parent);
		iterator.apply(instance.name);
	}

	@Override
	public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_parent));
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_name));
	}
		
}
