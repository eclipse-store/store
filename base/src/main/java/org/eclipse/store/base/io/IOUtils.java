package org.eclipse.store.base.io;

/*-
 * #%L
 * Eclipse Store Base utilities
 * %%
 * Copyright (C) 2019 - 2023 Eclipse Foundation
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import org.eclipse.store.base.chars.CharsUtils;
import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.io.XIO;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IOUtils
{

    public static final String addFileSuffix(final String fileName, final String fileSuffix)
    {
        return fileSuffix != null
                ? fileName + XIO.fileSuffixSeparator() + fileSuffix
                : fileName
                ;
    }

    public static final String getFilePrefix(final String fileName)
    {
        if (XChars.hasNoContent(fileName))
        {
            return null;
        }

        final int fileSuffixSeparatorIndex = fileName.lastIndexOf(XIO.fileSuffixSeparator());
        if (fileSuffixSeparatorIndex < 0)
        {
            return fileName;
        }

        return fileName.substring(0, fileSuffixSeparatorIndex);
    }

    public static final String[] splitPath(final Path path)
    {
        /*
         * Note on algorithm:
         * Path#iterator does not work, because it omits the root element.
         * Prepending the root element does not work because it has a trailing separator in its toString
         * representation (which is inconsistent to all other Path elements) and there is no proper "getIdentifier"
         * method or such in Path.
         * Besides, Path only stores a plain String and every operation has to inefficiently deconstruct that string.
         *
         * So the only reasonable and performance-wise best approach in the first place is to split the string
         * directly.
         *
         * But :
         * String#split cannot be used since the separator might be a regex meta character.
         * It could be quoted, but all this regex business gets into the realm of cracking a nut with a sledgehammer.
         *
         * So a simpler, more direct and in the end much faster approach is used.
         * This might very well become relevant if lots of Paths (e.g. tens of thousands when scanning a drive) have
         * to be processed.
         */

        // local variables for debugging purposes. Should be jitted out, anyway.
        final String pathString = path.toString();
        final String separator = path.getFileSystem()
                .getSeparator();

        return CharsUtils.splitSimple(pathString, separator);
    }

    public static final void truncate(
            final FileChannel fileChannel,
            final long newSize
    )
            throws IOException
    {
        fileChannel.truncate(newSize);
    }


    public static final long read(
            final FileChannel fileChannel,
            final ByteBuffer targetBuffer
    )
            throws IOException
    {
        return XIO.read(fileChannel, targetBuffer, 0, fileChannel.size());
    }

    public static final long read(
            final FileChannel fileChannel,
            final ByteBuffer targetBuffer,
            final long filePosition
    )
            throws IOException
    {
        return XIO.read(fileChannel, targetBuffer, filePosition, targetBuffer.remaining());
    }

    public static final VarString assemblePath(
            final VarString vs,
            final CharSequence... elements
    )
    {
        return CharsUtils.assembleSeparated(vs, XIO.filePathSeparator(), elements);
    }

    public static final void move(final Path sourceFile, final Path targetFile)
            throws IOException, RuntimeException
    {
        try
        {
            Files.move(sourceFile, targetFile);
        } catch (final IOException e)
        {
            throw e;
        } catch (final Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static final long write(
            final FileChannel                    fileChannel,
            final Iterable<? extends ByteBuffer> buffers
    )
            throws IOException
    {
        long writeCount = 0;

        for(final ByteBuffer buffer : buffers)
        {
            writeCount += writeToChannel(fileChannel, buffer);
        }

        return writeCount;
    }

    private static long writeToChannel(
            final FileChannel fileChannel,
            final ByteBuffer  buffer
    )
            throws IOException
    {
        long writeCount = 0;
        while(buffer.hasRemaining())
        {
            writeCount += fileChannel.write(buffer);
        }

        return writeCount;
    }

    public static final boolean delete(final Path path) throws IOException
    {
        return Files.deleteIfExists(path);
    }


    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    /**
     * Dummy constructor to prevent instantiation of this static-only utility class.
     *
     * @throws UnsupportedOperationException when called
     */
    private IOUtils()
    {
        // static only
        throw new UnsupportedOperationException();
    }
}
