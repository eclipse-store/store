package org.eclipse.store.base.collections;

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


import org.eclipse.serializer.util.X;

import java.util.function.Supplier;

public final class ArraysUtils {

    public static <T> T removeFromIndex(final T[] elements, final int size, final int i)
    {
        final T removed = elements[i];
        if(i + 1 == size)
        {
            elements[i] = null;
        }
        else
        {
            System.arraycopy(elements, i + 1, elements, i, size - i - 1);
            elements[size - 1] = null;
        }

        return removed;
    }

    public static final <T> T[] fill(
            final T[]                   array   ,
            final Supplier<? extends T> supplier
    )
    {
        return uncheckedFill(array, 0, array.length, supplier);
    }

    public static final <T> T[] uncheckedFill(
            final T[]                   array   ,
            final int                   offset  ,
            final int                   bound   ,
            final Supplier<? extends T> supplier
    )
    {
        for(int i = offset; i < bound; i++)
        {
            array[i] = supplier.get();
        }

        return array;
    }

    public static final boolean equals(final byte[] a, final byte[] a2, final int length)
    {
        if(a == a2)
        {
            return true;
        }
        if(a == null || a2 == null || a.length < length || a2.length < length)
        {
            return false;
        }

        for(int i = 0; i < length; i++)
        {
            if(a[i] != a2[i])
            {
                return false;
            }
        }

        return true;

    }

    public static <E> E[] copyRange(final E[] elements, final int offset, final int length)
    {
        final E[] copy = X.ArrayOfSameType(elements, length);
        System.arraycopy(elements, offset, copy, 0, length);
        return copy;
    }

    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    /**
     * Dummy constructor to prevent instantiation of this static-only utility class.
     *
     * @throws UnsupportedOperationException when called
     */
    private ArraysUtils()
    {
        // static only
        throw new UnsupportedOperationException();
    }
}
