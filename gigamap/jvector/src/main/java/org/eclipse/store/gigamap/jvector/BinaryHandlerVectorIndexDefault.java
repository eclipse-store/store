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
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceReferenceLoader;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;
import org.eclipse.store.gigamap.types.AbstractBinaryHandlerStateChangeFlagged;
import org.eclipse.store.gigamap.types.GigaMap;

/**
 * Binary persistence handler for {@link VectorIndex.Default}.
 * <p>
 * Handles serialization of persistent fields and reinitialization of
 * transient index components after deserialization.
 */
public class BinaryHandlerVectorIndexDefault
extends AbstractBinaryHandlerStateChangeFlagged<VectorIndex.Default<?>>
{
    ///////////////////////////////////////////////////////////////////////////
    // constants //
    //////////////

    private static final long
        BINARY_OFFSET_parent           =                                                            0,
        BINARY_OFFSET_name             = BINARY_OFFSET_parent        + Binary.objectIdByteLength(),
        BINARY_OFFSET_configuration    = BINARY_OFFSET_name          + Binary.objectIdByteLength(),
        BINARY_OFFSET_vectorizer       = BINARY_OFFSET_configuration + Binary.objectIdByteLength(),
        BINARY_OFFSET_vectorStore      = BINARY_OFFSET_vectorizer    + Binary.objectIdByteLength(),
        BINARY_LENGTH                  = BINARY_OFFSET_vectorStore   + Binary.objectIdByteLength(),

        MEMORY_OFFSET_parent           = getClassDeclaredFieldOffset(genericType(), "parent"       ),
        MEMORY_OFFSET_name             = getClassDeclaredFieldOffset(genericType(), "name"         ),
        MEMORY_OFFSET_configuration    = getClassDeclaredFieldOffset(genericType(), "configuration"),
        MEMORY_OFFSET_vectorizer       = getClassDeclaredFieldOffset(genericType(), "vectorizer"   ),
        MEMORY_OFFSET_vectorStore      = getClassDeclaredFieldOffset(genericType(), "vectorStore"  )
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
                CustomField(VectorIndices.class           , "parent"          ),
                CustomField(String.class                  , "name"            ),
                CustomField(VectorIndexConfiguration.class, "configuration"   ),
                CustomField(Vectorizer.class              , "vectorizer"      ),
                CustomField(GigaMap.class                 , "vectorStore"     )
            )
        );
    }


    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

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
        data.storeReference(BINARY_OFFSET_vectorStore  , handler, instance.vectorStore  );
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
        final GigaMap<float[]> vectorStore = (GigaMap<float[]>)data.readReference(
            BINARY_OFFSET_vectorStore, handler
        );

        XMemory.setObject(instance, MEMORY_OFFSET_parent       , parent       );
        XMemory.setObject(instance, MEMORY_OFFSET_name         , name         );
        XMemory.setObject(instance, MEMORY_OFFSET_configuration, configuration);
        XMemory.setObject(instance, MEMORY_OFFSET_vectorizer   , vectorizer   );
        XMemory.setObject(instance, MEMORY_OFFSET_vectorStore  , vectorStore  );
    }

    @Override
    public void complete(
        final Binary                 data    ,
        final VectorIndex.Default<?> instance,
        final PersistenceLoadHandler handler
    )
    {
        // Reinitialize transient components after all fields are loaded
        instance.ensureIndexInitialized();
    }

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
        iterator.apply(instance.vectorStore  );
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
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_vectorStore  ));
    }

}
