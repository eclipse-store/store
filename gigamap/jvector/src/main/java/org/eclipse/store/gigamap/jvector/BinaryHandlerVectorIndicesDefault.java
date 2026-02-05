package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
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
import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.serializer.persistence.types.PersistenceFunction;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceReferenceLoader;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;
import org.eclipse.store.gigamap.types.AbstractBinaryHandlerStateChangeFlagged;
import org.eclipse.store.gigamap.types.GigaMap;

/**
 * Binary persistence handler for {@link VectorIndices.Default}.
 */
public class BinaryHandlerVectorIndicesDefault
extends AbstractBinaryHandlerStateChangeFlagged<VectorIndices.Default<?>>
{
    ///////////////////////////////////////////////////////////////////////////
    // constants //
    //////////////

    private static final long
        BINARY_OFFSET_parent        =                                                         0,
        BINARY_OFFSET_vectorIndices = BINARY_OFFSET_parent        + Binary.objectIdByteLength(),
        BINARY_LENGTH               = BINARY_OFFSET_vectorIndices + Binary.objectIdByteLength(),

        MEMORY_OFFSET_parent        = getClassDeclaredFieldOffset(genericType(), "parent"),
        MEMORY_OFFSET_vectorIndices = getClassDeclaredFieldOffset(genericType(), "vectorIndices")
    ;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Class<VectorIndices.Default<?>> genericType()
    {
        return (Class)VectorIndices.Default.class;
    }


    public static BinaryHandlerVectorIndicesDefault New()
    {
        return new BinaryHandlerVectorIndicesDefault();
    }


    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    BinaryHandlerVectorIndicesDefault()
    {
        super(
            genericType(),
            CustomFields(
                CustomField(GigaMap.class    , "parent"       ),
                CustomField(EqHashTable.class, "vectorIndices")
            )
        );
    }


    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

    @Override
    protected void internalStore(
        final Binary                          data    ,
        final VectorIndices.Default<?>        instance,
        final long                            objectId,
        final PersistenceStoreHandler<Binary> handler
    )
    {
        data.storeEntityHeader(BINARY_LENGTH, this.typeId(), objectId);
        data.storeReference(BINARY_OFFSET_parent, handler, instance.parent);
        data.storeReferenceEager(BINARY_OFFSET_vectorIndices, handler, instance.vectorIndices);
    }

    @Override
    public VectorIndices.Default<?> create(
        final Binary                 data   ,
        final PersistenceLoadHandler handler
    )
    {
        return new VectorIndices.Default<>(null, null, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateState(
        final Binary                   data    ,
        final VectorIndices.Default<?> instance,
        final PersistenceLoadHandler   handler
    )
    {
        final GigaMap.Internal<?> parent = (GigaMap.Internal<?>)data.readReference(
            BINARY_OFFSET_parent, handler
        );
        final EqHashTable<String, VectorIndex.Internal<?>> vectorIndices =
            (EqHashTable<String, VectorIndex.Internal<?>>)data.readReference(
                BINARY_OFFSET_vectorIndices, handler
            );

        XMemory.setObject(instance, MEMORY_OFFSET_parent       , parent       );
        XMemory.setObject(instance, MEMORY_OFFSET_vectorIndices, vectorIndices);
    }

    @Override
    public void iterateInstanceReferences(
        final VectorIndices.Default<?> instance,
        final PersistenceFunction      iterator
    )
    {
        iterator.apply(instance.parent);
        Persistence.iterateReferences(iterator, instance.vectorIndices);
    }

    @Override
    public void iterateLoadableReferences(
        final Binary                     data    ,
        final PersistenceReferenceLoader iterator
    )
    {
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_parent       ));
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_vectorIndices));
    }

}
