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


import io.github.jbellis.jvector.quantization.NVQuantization;
import io.github.jbellis.jvector.quantization.ProductQuantization;

import java.nio.file.Path;
import java.util.List;

/**
 * Test helper: loads an index directory the way an index opens it, through a
 * {@link DiskIndexManager}, and holds the loaded segments until closed, so a test can inspect what
 * the files on disk hold without an index.
 */
final class SegmentLoad implements AutoCloseable
{
    private final DiskIndexManager             manager;
    private       DiskIndexManager.LoadedIndex loaded ;
    private       OwnerTable                   owners ;

    private SegmentLoad(final DiskIndexManager manager)
    {
        this.manager = manager;
    }

    /**
     * @return a loader over a fresh disk manager with the given arguments, nothing loaded yet
     */
    static SegmentLoad of(
        final DiskIndexManager.IndexStateProvider provider           ,
        final String                              name               ,
        final Path                                indexDirectory     ,
        final int                                 dimension          ,
        final GraphFormat                         format             ,
        final boolean                             parallelOnDiskWrite
    )
    {
        return new SegmentLoad(new DiskIndexManager.Default(
            provider, name, indexDirectory, dimension, format, parallelOnDiskWrite
        ));
    }

    /**
     * Loads against {@code expected}, closing what an earlier load opened.
     *
     * @param expected the witnesses the manifest must carry
     * @return whether it loaded
     */
    boolean tryLoad(final DiskIndexManager.MetaState expected)
    {
        this.close();
        this.owners = OwnerTable.New();
        this.loaded = this.manager.load(expected, this.owners);
        return this.loaded != null;
    }

    boolean isLoaded()
    {
        return this.loaded != null;
    }

    List<DiskSegment> segments()
    {
        return this.loaded == null ? List.of() : this.loaded.segments();
    }

    SegmentOwners owners()
    {
        return this.loaded == null ? null : this.owners.owners();
    }

    boolean hasSegmentsFlaggedForMerge()
    {
        return this.loaded != null && this.loaded.hasFlaggedSegments();
    }

    ProductQuantization loadedProductQuantization()
    {
        return this.loaded == null ? null : this.loaded.codebook();
    }

    NVQuantization loadedNVQuantization()
    {
        return this.loaded == null ? null : this.loaded.nvq();
    }

    boolean indexFilesExist()
    {
        return this.manager.indexFilesExist();
    }

    void deleteIndexFiles()
    {
        this.manager.deleteIndexFiles();
    }

    @Override
    public void close()
    {
        if(this.loaded != null)
        {
            this.loaded.segments().forEach(DiskSegment::close);
            this.loaded = null;
        }
    }

}
