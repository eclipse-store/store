package org.eclipse.store.base.memory;

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


import org.eclipse.serializer.memory.XMemory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class MemoryUtils {

    /**
     * Alias for {@code ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())}.
     * See {@link ByteBuffer#allocateDirect(int)} for details.
     *
     * @param capacity
     *         The new buffer's capacity, in bytes
     *
     * @return a newly created direct byte buffer with the specified capacity and the platform's native byte order.
     *
     * @throws IllegalArgumentException
     *         If the {@code capacity} is a negative integer.
     *
     * @see ByteBuffer#allocateDirect(int)
     * @see ByteBuffer#order(ByteOrder)
     */
    public static final ByteBuffer allocateDirectNative(final int capacity) throws IllegalArgumentException
    {
        return ByteBuffer
                .allocateDirect(capacity)
                .order(ByteOrder.nativeOrder())
                ;
    }

    public static final void free(final long address)
    {
        XMemory.MEMORY_ACCESSOR.freeMemory(address);
    }


    public static final ByteBuffer allocateDirectNativeDefault()
    {
        return allocateDirectNative(XMemory.defaultBufferSize());
    }

    // memory allocation //

    public static final long allocate(final long bytes)
    {
        return XMemory.MEMORY_ACCESSOR.allocateMemory(bytes);
    }

    /**
     * Parses a {@link String} instance to a {@link ByteOrder} instance according to {@code ByteOrder#toString()}
     * or throws an {@link IllegalArgumentException} if the passed string does not match exactly one of the
     * {@link ByteOrder} constant instances' string representation.
     *
     * @param name the string representing the {@link ByteOrder} instance to be parsed.
     * @return the recognized {@link ByteOrder}
     * @throws IllegalArgumentException if the string can't be recognized as a {@link ByteOrder} constant instance.
     * @see ByteOrder#toString()
     */
    public static final ByteOrder parseByteOrder(final String name)
    {
        if(name.equals(ByteOrder.BIG_ENDIAN.toString()))
        {
            return ByteOrder.BIG_ENDIAN;
        }
        if(name.equals(ByteOrder.LITTLE_ENDIAN.toString()))
        {
            return ByteOrder.LITTLE_ENDIAN;
        }

        throw new IllegalArgumentException("Unknown ByteOrder: \"" + name + "\"");
    }

    public static final byte[] toArray(final ByteBuffer source, final int position, final int length)
    {
        final long plState = XMemory.getPositionLimit(source);
        XMemory.setPositionLimit(source, position, position + length);

        final byte[] bytes = new byte[length];
        source.get(bytes, 0, length);

        // why would a querying methode intrinsically increase the position? WHY?
        XMemory.setPositionLimit(source, plState);

        return bytes;
    }

    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    /**
     * Dummy constructor to prevent instantiation of this static-only utility class.
     *
     * @throws UnsupportedOperationException when called
     */
    private MemoryUtils()
    {
        // static only
        throw new UnsupportedOperationException();
    }
}
