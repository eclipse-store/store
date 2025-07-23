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

import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceReferenceLoader;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;


/**
 * The BinaryHandlerCustomConstraintsDefault class is responsible for providing binary persistence
 * handling for the {@link CustomConstraints.Default} type. It extends the {@link AbstractBinaryHandlerStateChangeFlagged}
 * base class and implements the logic for storing, loading, and updating fields specific to
 * the {@link CustomConstraints.Default} type in a binary format.
 * <p>
 * This class works with references in binary data to manage dependencies such as parent objects
 * and elements. It also ensures that any necessary state changes in the object being handled
 * are properly updated during loading and persistence.
 * <p>
 * Key Responsibilities:
 * <ul>
 * <li>Storing data fields and references for a CustomConstraints.Default<?> instance into a binary format.</li>
 * <li>Restoring a CustomConstraints.Default<?> instance from binary data and associating it with its parent
 * and other dependencies.</li>
 * <li>Updating the state of a CustomConstraints.Default<?> instance when its binary data is loaded.</li>
 * <li>Iterating over loadable references to facilitate dependency resolution.</li>
 * </ul>
 */
public class BinaryHandlerCustomConstraintsDefault extends AbstractBinaryHandlerStateChangeFlagged<CustomConstraints.Default<?>>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
	
	private static final long
		BINARY_OFFSET_parent   =                                                    0,
		BINARY_OFFSET_elements = BINARY_OFFSET_parent   + Binary.objectIdByteLength(),
		BINARY_LENGTH          = BINARY_OFFSET_elements + Binary.objectIdByteLength(),

		MEMORY_OFFSET_parent   = getClassDeclaredFieldOffset(genericType(), "parent")
	;
	
	@SuppressWarnings("all")
	public static final Class<CustomConstraints.Default<?>> genericType()
	{
		// no idea how to get ".class" to work otherwise in conjunction with generics.
		return (Class)CustomConstraints.Default.class;
	}
	
	
	
	public static BinaryHandlerCustomConstraintsDefault New()
	{
		return new BinaryHandlerCustomConstraintsDefault();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	BinaryHandlerCustomConstraintsDefault()
	{
		super(
			genericType(),
			CustomFields(
				CustomField(GigaMap.class    , "parent"  ),
				CustomField(EqHashTable.class, "elements")
			)
		);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
			
	@Override
	protected void internalStore(
		final Binary                          data    ,
		final CustomConstraints.Default<?>    instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		data.storeEntityHeader(BINARY_LENGTH, this.typeId(), objectId);
		
		data.store_long(
			BINARY_OFFSET_parent,
			handler.apply(XMemory.getObject(instance, MEMORY_OFFSET_parent))
		);
		data.store_long(
			BINARY_OFFSET_elements,
			handler.apply(instance.elements())
		);
	}
		
	@Override
	public CustomConstraints.Default<?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		return new CustomConstraints.Default<>(null, null, false);
	}
	
	private static GigaMap<?> getParent(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (GigaMap<?>)data.readReference(BINARY_OFFSET_parent, handler);
	}

	@SuppressWarnings("unchecked")
	private static <E> EqHashTable<String, CustomConstraint<? super E>> getElements(
		final Binary                 data   ,
		final PersistenceLoadHandler handler
	)
	{
		return (EqHashTable<String, CustomConstraint<? super E>>)data.readReference(BINARY_OFFSET_elements, handler);
	}
		
	@Override
	public void updateState(
		final Binary                       data    ,
		final CustomConstraints.Default<?> instance,
		final PersistenceLoadHandler       handler
	)
	{
		// type erasure 4tl.
		this.internalUpdateState(data, instance, handler);
	}
	
	protected <E> void internalUpdateState(
		final Binary                       data    ,
		final CustomConstraints.Default<E> instance,
		final PersistenceLoadHandler       handler
	)
	{
		// setting the final field must be done directly, the optional field can be set by methods.
		XMemory.setObject(instance, MEMORY_OFFSET_parent, getParent(data, handler));
		instance.internalSetElements(getElements(data, handler));
	}
	
	@Override
	public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_parent));
		iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_elements));
	}
		
}
