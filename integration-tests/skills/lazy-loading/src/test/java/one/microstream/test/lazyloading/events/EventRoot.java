package one.microstream.test.lazyloading.events;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - lazy-loading
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

import org.eclipse.serializer.collections.lazy.LazyArrayList;

/**
 * SKILL Pattern E — segmented {@link LazyArrayList} held directly on the root.
 */
public class EventRoot
{
    private final LazyArrayList<Event> events = new LazyArrayList<>();

    public LazyArrayList<Event> events()
    {
        return this.events;
    }
}
