package org.eclipse.store.base;

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

import org.eclipse.store.base.collections.SynchList;
import org.eclipse.store.base.functional._intIndexedSupplier;
import org.eclipse.serializer.collections.ArrayView;
import org.eclipse.serializer.collections.types.XList;
import org.eclipse.serializer.concurrency.ThreadSafe;
import org.eclipse.serializer.util.X;

public final class Utils {
    @SafeVarargs
    public static <E> ArrayView<E> ArrayView(final E... elements)
    {
        if(elements == null || elements.length == 0)
        {
            return new ArrayView<>();
        }
        return new ArrayView<>(elements);
    }

    public static <E> E[] Array(final Class<E> componentType, final int length, final _intIndexedSupplier<E> supplier)
    {
        final E[] array = X.Array(componentType, length);

        for(int i = 0; i < array.length; i++)
        {
            array[i] = supplier.get(i);
        }

        return array;
    }

    /**
     * Helper method to project ternary values to binary logic.<br>
     * Useful for checking "really not true" (either false or unknown).
     *
     * @param b a {@code Boolean} object.
     * @return <code>true</code> if {@code b} is {@code null} or <code>false</code>, otherwise <code>false</code>
     */
    public static final boolean isNotTrue(final Boolean b)
    {
        return b == null ? true : !b;
    }

    /**
     * Helper method to project ternary values to binary logic.<br>
     * Useful for checking "really false" (not true and not unknown).
     *
     * @param b a {@code Boolean} object.
     * @return <code>false</code> if {@code b} is {@code null} or <code>true</code>, otherwise <code>true</code>
     */
    public static final boolean isFalse(final Boolean b)
    {
        return b == null ? false : !b;
    }

    /**
     * Ensures that the returned {@link XList} instance based on the passed list is thread safe to use.<br>
     * This normally means wrapping the passed list in a {@link SynchList}, making it effectively synchronized.<br>
     * If the passed list already is thread safe (indicated by the marker interface {@link ThreadSafe}), then the list
     * itself is returned without further actions. This automatically ensures that a {@link SynchList} is not
     * redundantly wrapped again in another {@link SynchList}.
     *
     * @param <E> the element type.
     * @param list the {@link XList} instance to be synchronized.
     * @return a thread safe {@link XList} using the passed list.
     */
    public static <E> XList<E> synchronize(final XList<E> list)
    {
        // if type of passed list is already thread safe, there's no need to wrap it in a SynchronizedXList
        if(list instanceof ThreadSafe)
        {
            return list;
        }
        // wrap not thread safe list types in a SynchronizedXList
        return new SynchList<>(list);
    }


    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    /**
     * Dummy constructor to prevent instantiation of this static-only utility class.
     *
     * @throws UnsupportedOperationException when called
     */
    private Utils()
    {
        // static only
        throw new UnsupportedOperationException();
    }
}
