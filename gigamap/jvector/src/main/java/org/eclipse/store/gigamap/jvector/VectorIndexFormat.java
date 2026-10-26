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

import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.exceptions.PersistenceException;

/**
 * The persistent format of a vector index: its record in the storage and its files beside it. Every stored
 * {@link VectorIndex.Default} references the format of the build that wrote it, so the format is checked when the
 * record is loaded, before any index file is opened.
 * <p>
 * The type itself keeps out the builds that predate it. They have no class of this name, so a storage holding one
 * fails at start with a missing runtime type. Without it they would map the record onto their own layout,
 * drop the vector state, and serve an empty index without an error. The builds that know the type refuse a
 * format whose {@link #minimumReaderVersion} is above their {@link #VERSION}: the files may have changed in a way
 * they would take for damage and rebuild or delete.
 * <p>
 * {@link #VERSION} is raised with every change of the record or of a file format (version 1: vector file 2, graph
 * and manifest 7, l2g 1). {@link #MINIMUM_READER_VERSION} is raised to it only when older builds can no longer
 * read what this build writes.
 */
final class VectorIndexFormat
{
    ///////////////////////////////////////////////////////////////////////////
    // constants //
    //////////////

    /** the format this build writes and the highest it reads */
    static final int VERSION                = 1;

    /** the oldest build format that can read what this build writes */
    static final int MINIMUM_READER_VERSION = 1;

    /** written by every record this build stores */
    static final VectorIndexFormat CURRENT = new VectorIndexFormat(VERSION, MINIMUM_READER_VERSION);



    ///////////////////////////////////////////////////////////////////////////
    // static methods //
    ///////////////////

    static BinaryTypeHandler<VectorIndexFormat> provideTypeHandler()
    {
        return BinaryHandlerVectorIndexFormat.New();
    }



    ///////////////////////////////////////////////////////////////////////////
    // instance fields //
    ////////////////////

    final int version             ;
    final int minimumReaderVersion;



    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    VectorIndexFormat(final int version, final int minimumReaderVersion)
    {
        this.version              = version             ;
        this.minimumReaderVersion = minimumReaderVersion;
    }



    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

    /**
     * Throws if this build cannot read the format.
     */
    VectorIndexFormat validateReadable()
    {
        if(this.minimumReaderVersion > VERSION)
        {
            throw new PersistenceException("A vector index in this storage was written in format " + this.version
                + ", which needs a build that reads format " + this.minimumReaderVersion + " or newer; this build"
                + " reads up to format " + VERSION + ". Open the storage with the version that wrote it.");
        }
        return this;
    }

    @Override
    public String toString()
    {
        return "VectorIndexFormat[version=" + this.version + ", minimumReaderVersion="
            + this.minimumReaderVersion + "]";
    }

}
