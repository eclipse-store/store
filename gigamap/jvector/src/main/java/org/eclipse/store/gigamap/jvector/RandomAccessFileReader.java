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

import io.github.jbellis.jvector.disk.RandomAccessReader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * RandomAccessReader implementation wrapping a RandomAccessFile.
 * Used for loading persisted graph indices from disk.
 */
class RandomAccessFileReader implements RandomAccessReader
{
    private final RandomAccessFile raf;

    RandomAccessFileReader(final RandomAccessFile raf)
    {
        this.raf = raf;
    }

    @Override
    public void seek(final long offset) throws IOException
    {
        this.raf.seek(offset);
    }

    @Override
    public long getPosition() throws IOException
    {
        return this.raf.getFilePointer();
    }

    @Override
    public int readInt() throws IOException
    {
        return this.raf.readInt();
    }

    @Override
    public float readFloat() throws IOException
    {
        return this.raf.readFloat();
    }

    @Override
    public long readLong() throws IOException
    {
        return this.raf.readLong();
    }

    @Override
    public void readFully(final byte[] bytes) throws IOException
    {
        this.raf.readFully(bytes);
    }

    @Override
    public void readFully(final ByteBuffer buffer) throws IOException
    {
        final byte[] bytes = new byte[buffer.remaining()];
        this.raf.readFully(bytes);
        buffer.put(bytes);
    }

    @Override
    public void readFully(final long[] vector) throws IOException
    {
        for(int i = 0; i < vector.length; i++)
        {
            vector[i] = this.raf.readLong();
        }
    }

    @Override
    public void read(final int[] ints, final int offset, final int count) throws IOException
    {
        for(int i = 0; i < count; i++)
        {
            ints[offset + i] = this.raf.readInt();
        }
    }

    @Override
    public void read(final float[] floats, final int offset, final int count) throws IOException
    {
        for(int i = 0; i < count; i++)
        {
            floats[offset + i] = this.raf.readFloat();
        }
    }

    @Override
    public void close() throws IOException
    {
        // Don't close the underlying RAF - it's managed externally
    }

    @Override
    public long length() throws IOException
    {
        return this.raf.length();
    }

}
