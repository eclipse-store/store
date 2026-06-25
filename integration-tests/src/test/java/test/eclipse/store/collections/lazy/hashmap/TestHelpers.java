package test.eclipse.store.collections.lazy.hashmap;

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


import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.eclipse.serializer.collections.lazy.LazySegment;

public class TestHelpers {

    static public void assertLoadedSegment(LazyHashMap<?, ?> map) {

        int expected = 0;

        try {
            final Field unloader = map.getClass()
                    .getDeclaredField("unloader");
            unloader.setAccessible(true);
            final Field load = unloader.get(map)
                    .getClass()
                    .getDeclaredField("desiredLoadCount");
            load.setAccessible(true);
            final Object ul = unloader.get(map);
            expected = load.getInt(ul);

        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        assertTrue(getLoadedSegments(map).size() <= expected);
    }

    static public List<LazySegment<?>> getLoadedSegments(LazyHashMap<?, ?> map) {

        final Iterable<? extends LazyHashMap<?, ?>.Segment<?>> segments = map.segments();

        final List<LazySegment<?>> loadedSegments = new ArrayList<>();
        segments.forEach(s -> {
            if (s.isLoaded()) {
                loadedSegments.add(s);
            }
        });

        return loadedSegments;
    }
}
