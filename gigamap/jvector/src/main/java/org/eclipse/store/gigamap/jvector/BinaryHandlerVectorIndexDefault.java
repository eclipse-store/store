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

import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceFunction;
import org.eclipse.serializer.persistence.types.PersistenceLegacyTypeHandler;
import org.eclipse.serializer.persistence.types.PersistenceLegacyTypeHandlerSupplier;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceReferenceLoader;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;
import org.eclipse.store.gigamap.types.AbstractBinaryHandlerStateChangeFlagged;
import org.eclipse.store.gigamap.types.GigaMap;

import java.util.List;

/**
 * Binary persistence handler for {@link VectorIndex.Default}.
 * <p>
 * Handles serialization of persistent fields and reinitialization of
 * transient index components after deserialization.
 */
public class BinaryHandlerVectorIndexDefault
extends AbstractBinaryHandlerStateChangeFlagged<VectorIndex.Default<?>>
implements PersistenceLegacyTypeHandlerSupplier<Binary, VectorIndex.Default<?>>
{
    ///////////////////////////////////////////////////////////////////////////
    // constants //
    //////////////

    private static final long
        BINARY_OFFSET_parent             =                                                                   0,
        BINARY_OFFSET_name               = BINARY_OFFSET_parent               + Binary.objectIdByteLength(),
        BINARY_OFFSET_configuration      = BINARY_OFFSET_name                 + Binary.objectIdByteLength(),
        BINARY_OFFSET_vectorizer         = BINARY_OFFSET_configuration        + Binary.objectIdByteLength(),
        BINARY_OFFSET_format             = BINARY_OFFSET_vectorizer           + Binary.objectIdByteLength(),
        BINARY_OFFSET_structuralModCount = BINARY_OFFSET_format               + Binary.objectIdByteLength(),
        BINARY_OFFSET_vectorFileNonce    = BINARY_OFFSET_structuralModCount   + Long.BYTES                  ,
        BINARY_OFFSET_vectorFileEpoch    = BINARY_OFFSET_vectorFileNonce      + Long.BYTES                  ,
        BINARY_OFFSET_vectorCount        = BINARY_OFFSET_vectorFileEpoch      + Long.BYTES                  ,
        BINARY_LENGTH                    = BINARY_OFFSET_vectorCount          + Long.BYTES                  ,

        MEMORY_OFFSET_parent             = getClassDeclaredFieldOffset(genericType(), "parent"            ),
        MEMORY_OFFSET_name               = getClassDeclaredFieldOffset(genericType(), "name"              ),
        MEMORY_OFFSET_configuration      = getClassDeclaredFieldOffset(genericType(), "configuration"     ),
        MEMORY_OFFSET_vectorizer         = getClassDeclaredFieldOffset(genericType(), "vectorizer"        ),
        MEMORY_OFFSET_structuralModCount = getClassDeclaredFieldOffset(genericType(), "structuralModCount"),
        MEMORY_OFFSET_vectorFileNonce    = getClassDeclaredFieldOffset(genericType(), "vectorFileNonce"   ),
        MEMORY_OFFSET_vectorFileEpoch    = getClassDeclaredFieldOffset(genericType(), "vectorFileEpoch"   ),
        MEMORY_OFFSET_vectorCount        = getClassDeclaredFieldOffset(genericType(), "vectorCount"       )
    ;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Class<VectorIndex.Default<?>> genericType()
    {
        return (Class)VectorIndex.Default.class;
    }


    public static BinaryHandlerVectorIndexDefault New()
    {
        return new BinaryHandlerVectorIndexDefault();
    }


    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    BinaryHandlerVectorIndexDefault()
    {
        super(
            genericType(),
            CustomFields(
                CustomField(VectorIndices.class           , "parent"            ),
                CustomField(String.class                  , "name"              ),
                CustomField(VectorIndexConfiguration.class, "configuration"     ),
                CustomField(Vectorizer.class              , "vectorizer"        ),
                CustomField(VectorIndexFormat.class       , "format"            ),
                CustomField(long.class                    , "structuralModCount"),
                CustomField(long.class                    , "vectorFileNonce"   ),
                CustomField(long.class                    , "vectorFileEpoch"   ),
                CustomField(long.class                    , "vectorCount"       )
            )
        );
    }


    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

    /**
     * Seals the vector file's epoch into the record before it is written, the same hook the Lucene index
     * uses to commit on store: {@code internalStore} below then writes the epoch the seal produced, and
     * {@code super.store} sees the instance as changed if the seal changed it.
     */
    @Override
    public void store(
        final Binary                          data    ,
        final VectorIndex.Default<?>          instance,
        final long                            objectId,
        final PersistenceStoreHandler<Binary> handler
    )
    {
        final Runnable hook = instance.storeRecordTestHook;
        if(hook != null)
        {
            hook.run();
        }
        final String refusal = instance.legacyVectorStoreRefusal();
        if(refusal != null)
        {
            // This layout has no field for the vector store: written now, the record would lose the only
            // reference to the vectors. The first access imports them (ensureVectorsRecovered); an index that keeps
            // no files holds on to the store until it gets a location.
            throw new IllegalStateException(refusal);
        }
        instance.internalSealVectorEpochOnStore(handler);
        super.store(data, instance, objectId, handler);
    }

    @Override
    protected void internalStore(
        final Binary                          data    ,
        final VectorIndex.Default<?>          instance,
        final long                            objectId,
        final PersistenceStoreHandler<Binary> handler
    )
    {
        data.storeEntityHeader(BINARY_LENGTH, this.typeId(), objectId);
        data.storeReference(BINARY_OFFSET_parent       , handler, instance.parent       );
        data.storeReference(BINARY_OFFSET_name         , handler, instance.name         );
        data.storeReference(BINARY_OFFSET_configuration, handler, instance.configuration);
        data.storeReference(BINARY_OFFSET_vectorizer   , handler, instance.vectorizer   );
        data.storeReference(BINARY_OFFSET_format       , handler, VectorIndexFormat.CURRENT);
        data.store_long    (BINARY_OFFSET_structuralModCount, instance.structuralModCount);
        data.store_long    (BINARY_OFFSET_vectorFileNonce   , instance.vectorFileNonce   );
        data.store_long    (BINARY_OFFSET_vectorFileEpoch   , instance.vectorFileEpoch   );
        data.store_long    (BINARY_OFFSET_vectorCount       , instance.vectorCount       );
    }

    @Override
    public VectorIndex.Default<?> create(
        final Binary                 data   ,
        final PersistenceLoadHandler handler
    )
    {
        // Create minimal instance - fields will be set in updateState
        return new VectorIndex.Default<>();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateState(
        final Binary                 data    ,
        final VectorIndex.Default<?> instance,
        final PersistenceLoadHandler handler
    )
    {
        final VectorIndices<?> parent = (VectorIndices<?>)data.readReference(
            BINARY_OFFSET_parent, handler
        );
        final String name = (String)data.readReference(
            BINARY_OFFSET_name, handler
        );
        final VectorIndexConfiguration configuration = (VectorIndexConfiguration)data.readReference(
            BINARY_OFFSET_configuration, handler
        );
        final Vectorizer<?> vectorizer = (Vectorizer<?>)data.readReference(
            BINARY_OFFSET_vectorizer, handler
        );
        final long structuralModCount = data.read_long(BINARY_OFFSET_structuralModCount);
        final long vectorFileNonce    = data.read_long(BINARY_OFFSET_vectorFileNonce   );
        final long vectorFileEpoch    = data.read_long(BINARY_OFFSET_vectorFileEpoch   );
        final long vectorCount        = data.read_long(BINARY_OFFSET_vectorCount       );

        XMemory.setObject(instance, MEMORY_OFFSET_parent       , parent       );
        XMemory.setObject(instance, MEMORY_OFFSET_name         , name         );
        XMemory.setObject(instance, MEMORY_OFFSET_configuration, configuration);
        XMemory.setObject(instance, MEMORY_OFFSET_vectorizer   , vectorizer   );
        XMemory.set_long (instance, MEMORY_OFFSET_structuralModCount, structuralModCount);
        XMemory.set_long (instance, MEMORY_OFFSET_vectorFileNonce   , vectorFileNonce   );
        XMemory.set_long (instance, MEMORY_OFFSET_vectorFileEpoch   , vectorFileEpoch   );
        XMemory.set_long (instance, MEMORY_OFFSET_vectorCount       , vectorCount       );
    }

    @Override
    public void complete(
        final Binary                 data    ,
        final VectorIndex.Default<?> instance,
        final PersistenceLoadHandler handler
    )
    {
        // Reinitialize transient components after all fields are loaded. Only the nested-load-free
        // setup runs here, the vector file's open included; its repairs and the graph rebuild (which read
        // entities and would perform a nested object-graph load inside this enclosing load's
        // completeInstances phase) are deferred to the first search/mutation via
        // VectorIndex.ensureIndexInitialized().
        instance.initializeAfterLoad();
    }

    /**
     * The 4.2.0 layout, for a mapper that asks for one legacy handler; see {@link #getLegacyTypeHandlers()}.
     */
    @Override
    public PersistenceLegacyTypeHandler<Binary, VectorIndex.Default<?>> getLegacyTypeHandler()
    {
        return BinaryLegacyTypeHandlerVectorIndexDefault.New(true);
    }

    /**
     * Both released layouts before the vector file: 4.2.0 with {@code structuralModCount}, 4.0.0 to 4.1.0
     * without. The mapper picks the one whose structure the stored type definition has.
     */
    @Override
    public Iterable<PersistenceLegacyTypeHandler<Binary, VectorIndex.Default<?>>> getLegacyTypeHandlers()
    {
        return List.of(
            BinaryLegacyTypeHandlerVectorIndexDefault.New(true ),
            BinaryLegacyTypeHandlerVectorIndexDefault.New(false)
        );
    }

    // Provided only for PersistenceTypeHandler contract conformity. The standard store path
    // registers child references through handler.apply(...) callbacks while writing the
    // binary form, so this iterator is exercised only by niche traversals such as
    // PersistenceRegisterer.
    @Override
    public void iterateInstanceReferences(
        final VectorIndex.Default<?> instance,
        final PersistenceFunction    iterator
    )
    {
        iterator.apply(instance.parent       );
        iterator.apply(instance.name         );
        iterator.apply(instance.configuration);
        iterator.apply(instance.vectorizer   );
        iterator.apply(VectorIndexFormat.CURRENT);
    }

    @Override
    public void iterateLoadableReferences(
        final Binary                     data    ,
        final PersistenceReferenceLoader iterator
    )
    {
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_parent       ));
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_name         ));
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_configuration));
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_vectorizer   ));
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_format       ));
    }

}
