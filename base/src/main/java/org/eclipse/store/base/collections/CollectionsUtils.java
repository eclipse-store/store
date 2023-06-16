package org.eclipse.store.base.collections;

/*-
 * #%L
 * Eclipse Store Base utilities
 * %%
 * Copyright (C) 2023 Eclipse Foundation
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


import org.eclipse.serializer.collections.EqHashTable;

public final class CollectionsUtils {


    @SafeVarargs
    public static final <V> EqHashTable<Integer, V> toTable(final V... values)
    {
        final EqHashTable<Integer, V> table = EqHashTable.New();

        for(int i = 0; i < values.length; i++)
        {
            table.add(i, values[i]);
        }

        return table;
    }

    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    /**
     * Dummy constructor to prevent instantiation of this static-only utility class.
     *
     * @throws UnsupportedOperationException when called
     */
    private CollectionsUtils()
    {
        // static only
        throw new UnsupportedOperationException();
    }
}
