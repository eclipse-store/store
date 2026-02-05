package org.eclipse.store.gigamap.lucene;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
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

import java.util.concurrent.ConcurrentHashMap;

public class BinaryHandlerLuceneIndexDefault extends AbstractBinaryHandlerStateChangeFlagged<LuceneIndex.Default<?>>
{
    private static final long
        BINARY_OFFSET_gigaMap     =                                                       0,
        BINARY_OFFSET_context     = BINARY_OFFSET_gigaMap     + Binary.objectIdByteLength(),
        BINARY_OFFSET_fileEntries = BINARY_OFFSET_context     + Binary.objectIdByteLength(),
        BINARY_LENGTH             = BINARY_OFFSET_fileEntries + Binary.objectIdByteLength(),

        MEMORY_OFFSET_gigaMap     = getClassDeclaredFieldOffset(genericType(), "gigaMap"),
        MEMORY_OFFSET_context     = getClassDeclaredFieldOffset(genericType(), "context"),
        MEMORY_OFFSET_fileEntries = getClassDeclaredFieldOffset(genericType(), "fileEntries")
    ;

    @SuppressWarnings("all")
    public static final Class<LuceneIndex.Default<?>> genericType()
    {
        // no idea how to get ".class" to work otherwise in conjunction with generics.
        return (Class)LuceneIndex.Default.class;
    }



    public static BinaryHandlerLuceneIndexDefault New()
    {
        return new BinaryHandlerLuceneIndexDefault();
    }



    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    BinaryHandlerLuceneIndexDefault()
    {
        super(
            genericType(),
            CustomFields(
                CustomField(GigaMap.class          , "gigaMap"    ),
                CustomField(LuceneContext.class    , "context"    ),
                CustomField(ConcurrentHashMap.class, "fileEntries")
            )
        );
    }



    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

    @Override
    protected void internalStore(
        final Binary                          data    ,
        final LuceneIndex.Default<?>          instance,
        final long                            objectId,
        final PersistenceStoreHandler<Binary> handler
    )
    {
        data.storeEntityHeader(BINARY_LENGTH, this.typeId(), objectId);
        data.storeReference   (BINARY_OFFSET_gigaMap    , handler, instance.gigaMap    );
        data.storeReference   (BINARY_OFFSET_context    , handler, instance.context    );
        data.storeReference   (BINARY_OFFSET_fileEntries, handler, instance.fileEntries);
    }

    @Override
    public LuceneIndex.Default<?> create(
        final Binary                 data   ,
        final PersistenceLoadHandler handler
    )
    {
        return new LuceneIndex.Default<>(null, null, false);
    }

    private static GigaMap<?> getGigaMap(
        final Binary                 data   ,
        final PersistenceLoadHandler handler
    )
    {
        return (GigaMap<?>)data.readReference(BINARY_OFFSET_gigaMap, handler);
    }

    private static LuceneContext<?> getLuceneContext(
        final Binary                 data   ,
        final PersistenceLoadHandler handler
    )
    {
        return (LuceneContext<?>)data.readReference(BINARY_OFFSET_context, handler);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, LuceneIndex.Default.FileEntry> getFileEntries(
        final Binary                 data   ,
        final PersistenceLoadHandler handler
    )
    {
        return (ConcurrentHashMap<String, LuceneIndex.Default.FileEntry>)data.readReference(BINARY_OFFSET_fileEntries, handler);
    }

    @Override
    public void updateState(
        final Binary                 data    ,
        final LuceneIndex.Default<?> instance,
        final PersistenceLoadHandler handler
    )
    {
        // type erasure 4tl.
        this.internalUpdateState(data, instance, handler);
    }

    protected <E> void internalUpdateState(
        final Binary                 data    ,
        final LuceneIndex.Default<E> instance,
        final PersistenceLoadHandler handler
    )
    {
        XMemory.setObject(instance, MEMORY_OFFSET_gigaMap    , getGigaMap(data, handler));
        XMemory.setObject(instance, MEMORY_OFFSET_context    , getLuceneContext(data, handler));
        XMemory.setObject(instance, MEMORY_OFFSET_fileEntries, getFileEntries(data, handler));
    }

    @Override
    public void iterateInstanceReferences(final LuceneIndex.Default<?> instance, final PersistenceFunction iterator)
    {
        iterator.apply(instance.gigaMap);
        iterator.apply(instance.context);
        iterator.apply(instance.fileEntries);
    }

    @Override
    public void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
    {
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_gigaMap    ));
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_context    ));
        iterator.acceptObjectId(data.readObjectId(BINARY_OFFSET_fileEntries));
    }

}
