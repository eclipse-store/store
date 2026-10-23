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

import org.eclipse.serializer.collections.types.XGettingSequence;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.binary.types.BinaryLegacyTypeHandler;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceReferenceLoader;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinitionMember;
import org.eclipse.store.gigamap.types.GigaMap;

/**
 * Legacy handler for the two persisted layouts of {@link VectorIndex.Default} released before the vector file:
 * {@code (parent, name, configuration, vectorizer, vectorStore)} of 4.0.0 to 4.1.0, and the same plus
 * {@code structuralModCount} of 4.2.0. Both kept the vectors of a computed index in a {@code GigaMap} of
 * {@link VectorEntry}; the current layout keeps them in the index's vector file and persists its nonce, epoch and
 * vector count instead.
 * <p>
 * The handler creates the current instance with the old field values and hands it the old vector store
 * transiently. At the index's first access (or when a sibling index of the same group imports its own), in place
 * of the vector file's own recovery, which would call the vectorizer once per entity, the index imports every
 * vector of that store into its vector file and drops the store
 * ({@code VectorIndex.Default#importLegacyVectorStore}). The next store of the map persists the current layout;
 * the old vector store becomes unreferenced and is reclaimed by housekeeping. An embedded index never had a
 * vector store, so for it this handler only maps the layout.
 */
public class BinaryLegacyTypeHandlerVectorIndexDefault
extends BinaryLegacyTypeHandler.AbstractCustom<VectorIndex.Default<?>>
{
    ///////////////////////////////////////////////////////////////////////////
    // constants //
    //////////////

    private static final long
        BINARY_OFFSET_parent             =                                                            0,
        BINARY_OFFSET_name               = BINARY_OFFSET_parent        + Binary.objectIdByteLength(),
        BINARY_OFFSET_configuration      = BINARY_OFFSET_name          + Binary.objectIdByteLength(),
        BINARY_OFFSET_vectorizer         = BINARY_OFFSET_configuration + Binary.objectIdByteLength(),
        BINARY_OFFSET_vectorStore        = BINARY_OFFSET_vectorizer    + Binary.objectIdByteLength(),
        BINARY_OFFSET_structuralModCount = BINARY_OFFSET_vectorStore   + Binary.objectIdByteLength(),

        MEMORY_OFFSET_parent             = getClassDeclaredFieldOffset(BinaryHandlerVectorIndexDefault.genericType(), "parent"            ),
        MEMORY_OFFSET_name               = getClassDeclaredFieldOffset(BinaryHandlerVectorIndexDefault.genericType(), "name"              ),
        MEMORY_OFFSET_configuration      = getClassDeclaredFieldOffset(BinaryHandlerVectorIndexDefault.genericType(), "configuration"     ),
        MEMORY_OFFSET_vectorizer         = getClassDeclaredFieldOffset(BinaryHandlerVectorIndexDefault.genericType(), "vectorizer"        ),
        MEMORY_OFFSET_structuralModCount = getClassDeclaredFieldOffset(BinaryHandlerVectorIndexDefault.genericType(), "structuralModCount")
    ;


    /**
     * @param withStructuralModCount {@code true} for the 4.2.0 layout, {@code false} for 4.0.0 to 4.1.0
     */
    public static BinaryLegacyTypeHandlerVectorIndexDefault New(final boolean withStructuralModCount)
    {
        return new BinaryLegacyTypeHandlerVectorIndexDefault(withStructuralModCount);
    }

    private static XGettingSequence<? extends PersistenceTypeDefinitionMember> fields(final boolean withStructuralModCount)
    {
        return withStructuralModCount
            ? CustomFields(
                CustomField(VectorIndices.class           , "parent"            ),
                CustomField(String.class                  , "name"              ),
                CustomField(VectorIndexConfiguration.class, "configuration"     ),
                CustomField(Vectorizer.class              , "vectorizer"        ),
                CustomField(GigaMap.class                 , "vectorStore"       ),
                CustomField(long.class                    , "structuralModCount")
            )
            : CustomFields(
                CustomField(VectorIndices.class           , "parent"       ),
                CustomField(String.class                  , "name"         ),
                CustomField(VectorIndexConfiguration.class, "configuration"),
                CustomField(Vectorizer.class              , "vectorizer"   ),
                CustomField(GigaMap.class                 , "vectorStore"  )
            )
        ;
    }



    ///////////////////////////////////////////////////////////////////////////
    // instance fields //
    ////////////////////

    private final boolean withStructuralModCount;



    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    BinaryLegacyTypeHandlerVectorIndexDefault(final boolean withStructuralModCount)
    {
        super(BinaryHandlerVectorIndexDefault.genericType(), fields(withStructuralModCount));
        this.withStructuralModCount = withStructuralModCount;
    }



    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

    @Override
    public VectorIndex.Default<?> create(final Binary data, final PersistenceLoadHandler handler)
    {
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
        final VectorIndices<?>         parent        = (VectorIndices<?>)data.readReference(BINARY_OFFSET_parent, handler);
        final String                   name          = (String)data.readReference(BINARY_OFFSET_name, handler);
        final VectorIndexConfiguration configuration = (VectorIndexConfiguration)data.readReference(BINARY_OFFSET_configuration, handler);
        final Vectorizer<?>            vectorizer    = (Vectorizer<?>)data.readReference(BINARY_OFFSET_vectorizer, handler);
        final GigaMap<VectorEntry>     vectorStore   = (GigaMap<VectorEntry>)data.readReference(BINARY_OFFSET_vectorStore, handler);
        final long                     modCount      = this.withStructuralModCount
            ? data.read_long(BINARY_OFFSET_structuralModCount)
            : 0L
        ;

        XMemory.setObject(instance, MEMORY_OFFSET_parent       , parent       );
        XMemory.setObject(instance, MEMORY_OFFSET_name         , name         );
        XMemory.setObject(instance, MEMORY_OFFSET_configuration, configuration);
        XMemory.setObject(instance, MEMORY_OFFSET_vectorizer   , vectorizer   );
        XMemory.set_long (instance, MEMORY_OFFSET_structuralModCount, modCount);
        // nonce, epoch and count stay 0: the import sets them, and a 0 nonce makes the open replace any file
        instance.legacyVectorStore = vectorStore;
        instance.legacyLayout      = true;
    }

    @Override
    public void complete(
        final Binary                 data    ,
        final VectorIndex.Default<?> instance,
        final PersistenceLoadHandler handler
    )
    {
        // as the current handler: the nested-load-free setup only; the import and the rebuild run on first access
        instance.initializeAfterLoad();
    }

    @Override
    public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
    {
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_parent       ));
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_name         ));
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_configuration));
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_vectorizer   ));
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_vectorStore  ));
    }

}
