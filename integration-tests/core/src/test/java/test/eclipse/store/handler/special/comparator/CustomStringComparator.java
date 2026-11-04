package test.eclipse.store.handler.special.comparator;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import java.util.Comparator;

public class CustomStringComparator implements Comparator<String>
{

    @Override
    public int compare(String o1, String o2)
    {
        return o1.toLowerCase().compareTo(o2.toLowerCase());
    }
}
