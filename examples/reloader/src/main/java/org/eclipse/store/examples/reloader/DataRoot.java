package org.eclipse.store.examples.reloader;

/*-
 * #%L
 * EclipseStore Example Reloader
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.util.ArrayList;
import java.util.List;

public class DataRoot
{

    private final List<String> data = new ArrayList<>();

    public List<String> getData()
    {
        return this.data;
    }

    public void addItem(final String value) {
        this.data.add(value);
    }

}
