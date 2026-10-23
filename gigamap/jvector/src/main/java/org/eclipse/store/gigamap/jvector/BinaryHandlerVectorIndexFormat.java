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

import org.eclipse.serializer.persistence.binary.types.AbstractBinaryHandlerCustomValueFixedLength;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;

/**
 * Binary persistence handler for {@link VectorIndexFormat}. A format this build cannot read is refused when it is
 * created, i.e. when the record referencing it is loaded.
 */
final class BinaryHandlerVectorIndexFormat
extends AbstractBinaryHandlerCustomValueFixedLength<VectorIndexFormat, Long>
{
    ///////////////////////////////////////////////////////////////////////////
    // constants //
    //////////////

    private static final long
        BINARY_OFFSET_version              = 0                                              ,
        BINARY_OFFSET_minimumReaderVersion = BINARY_OFFSET_version              + Integer.BYTES,
        BINARY_LENGTH                      = BINARY_OFFSET_minimumReaderVersion + Integer.BYTES
    ;



    ///////////////////////////////////////////////////////////////////////////
    // static methods //
    ///////////////////

    static BinaryHandlerVectorIndexFormat New()
    {
        return new BinaryHandlerVectorIndexFormat();
    }



    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    BinaryHandlerVectorIndexFormat()
    {
        super(
            VectorIndexFormat.class,
            CustomFields(
                CustomField(int.class, "version"             ),
                CustomField(int.class, "minimumReaderVersion")
            )
        );
    }



    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

    private static long state(final int version, final int minimumReaderVersion)
    {
        return (long)version << Integer.SIZE | minimumReaderVersion & 0xFFFF_FFFFL;
    }

    @Override
    public void store(
        final Binary                          data    ,
        final VectorIndexFormat               instance,
        final long                            objectId,
        final PersistenceStoreHandler<Binary> handler
    )
    {
        data.storeEntityHeader(BINARY_LENGTH, this.typeId(), objectId);
        data.store_int(BINARY_OFFSET_version             , instance.version             );
        data.store_int(BINARY_OFFSET_minimumReaderVersion, instance.minimumReaderVersion);
    }

    @Override
    public VectorIndexFormat create(final Binary data, final PersistenceLoadHandler handler)
    {
        return new VectorIndexFormat(
            data.read_int(BINARY_OFFSET_version             ),
            data.read_int(BINARY_OFFSET_minimumReaderVersion)
        ).validateReadable();
    }

    @Override
    public Long getValidationStateFromInstance(final VectorIndexFormat instance)
    {
        return state(instance.version, instance.minimumReaderVersion);
    }

    @Override
    public Long getValidationStateFromBinary(final Binary data)
    {
        return state(
            data.read_int(BINARY_OFFSET_version             ),
            data.read_int(BINARY_OFFSET_minimumReaderVersion)
        );
    }

}
