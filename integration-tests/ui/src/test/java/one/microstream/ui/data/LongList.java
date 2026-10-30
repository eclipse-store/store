package one.microstream.ui.data;

/*-
 * #%L
 * EclipseStore Integration Tests - UI
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

import java.util.Random;

public class LongList implements BinaryHandlerTestData {

    int[] array;
    Random random = new Random();
    Object object;

    @Override
    public void fillSampleData() {
        array = random.ints(100000, 10, 100000).toArray();

    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }
}
