package test.eclipse.store.library.types;

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

import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConcurrentHashMapData implements BinaryHandlerTestData {
    ConcurrentHashMap<Integer, PrimitiveTypes> value = new ConcurrentHashMap<>();

    // additional corner-case maps
    private ConcurrentHashMap<Integer, PrimitiveTypes> valueAliased = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, PrimitiveTypes> valueSubclass = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Object, Object> valueHetero = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, PrimitiveTypes> largeValue = new ConcurrentHashMap<>();
    private ConcurrentHashMap<BadHashKey, String> badHashMap = new ConcurrentHashMap<>();

    @Override
    public ConcurrentHashMapData fillSampleData() {
        value.put(1, PrimitiveTypes.fillSample());

        // aliased values: same instance stored under multiple keys
        PrimitiveTypes shared = PrimitiveTypes.fillSample();
        shared.fillSampleData();
        valueAliased.put(1, shared);
        valueAliased.put(2, shared);

        // subclass value
        ExtendedPrimitive ep = new ExtendedPrimitive();
        ep.fillSampleData();
        ep.tag = "ext-1";
        valueSubclass.put(1, ep);

        // heterogenous map: different key/value types
        valueHetero.put("one", 1);
        valueHetero.put(2, "two");
        valueHetero.put(3, PrimitiveTypes.fillSample());

        // large map
        for (int i = 0; i < 2048; i++) {
            PrimitiveTypes p = PrimitiveTypes.fillSample();
            largeValue.put(i, p);
        }

        // bad hash keys (same hashCode) to stress bucket handling
        badHashMap.put(new BadHashKey(1), "v1");
        badHashMap.put(new BadHashKey(2), "v2");
        badHashMap.put(new BadHashKey(3), "v3");

        return this;
    }

    @Override
    public BinaryHandlerTestData updateSampleData() {
        value.put(2, new PrimitiveTypes());

        // update aliased: modify the shared instance
        PrimitiveTypes shared = valueAliased.get(1);
        if (shared != null) {
            // change a primitive field (via reflection-like setter not available) - recreate
            shared = PrimitiveTypes.fillSample();
            valueAliased.put(1, shared);
            valueAliased.put(2, shared);
        }

        // subclass modification
        PrimitiveTypes ep = valueSubclass.get(1);
        if (ep instanceof ExtendedPrimitive) {
            ((ExtendedPrimitive) ep).tag = "ext-updated";
            valueSubclass.put(1, ep);
        }

        // hetero: add an array value
        valueHetero.put("arr", new int[]{1, 2, 3});

        // large map: add more entries
        for (int i = 2048; i < 2060; i++) {
            largeValue.put(i, PrimitiveTypes.fillSample());
        }

        // badHashMap: add another collide key
        badHashMap.put(new BadHashKey(4), "v4");

        return this;
    }

    ConcurrentHashMap<Integer, PrimitiveTypes> getValue() {
        return value;
    }

    public ConcurrentHashMap<Integer, PrimitiveTypes> getValueAliased() {
        return valueAliased;
    }

    public ConcurrentHashMap<Integer, PrimitiveTypes> getValueSubclass() {
        return valueSubclass;
    }

    public ConcurrentHashMap<Object, Object> getValueHetero() {
        return valueHetero;
    }

    public ConcurrentHashMap<Integer, PrimitiveTypes> getLargeValue() {
        return largeValue;
    }

    public ConcurrentHashMap<BadHashKey, String> getBadHashMap() {
        return badHashMap;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        ConcurrentHashMapData copy = (ConcurrentHashMapData) o;

        // compare primary map via equals (map equality checks entries)
        assertAll("ConcurrentHashMapData",
                () -> assertEquals(this.getValue(), copy.getValue(), "value map equality"),
                () -> assertEquals(this.getValueAliased().size(), copy.getValueAliased().size(), "valueAliased size"),
                () -> assertEquals(this.getValueSubclass().size(), copy.getValueSubclass().size(), "valueSubclass size"),
                () -> assertEquals(this.getValueHetero().size(), copy.getValueHetero().size(), "valueHetero size"),
                () -> assertEquals(this.getLargeValue().size(), copy.getLargeValue().size(), "largeValue size"),
                () -> assertEquals(this.getBadHashMap().size(), copy.getBadHashMap().size(), "badHashMap size")
        );

        // alias identity: after reload both entries should reference the same object instance
        if (!this.getValueAliased().isEmpty()) {
            PrimitiveTypes a1 = copy.getValueAliased().get(1);
            PrimitiveTypes a2 = copy.getValueAliased().get(2);
            Assertions.assertSame(a1, a2, "aliased values should preserve identity after reload");
        }

        // subclass type preserved
        if (!this.getValueSubclass().isEmpty()) {
            PrimitiveTypes v = copy.getValueSubclass().get(1);
            Assertions.assertTrue(v instanceof ExtendedPrimitive, "valueSubclass item should be instance of ExtendedPrimitive");
            if (v instanceof ExtendedPrimitive) {
                Assertions.assertEquals(((ExtendedPrimitive) v).tag, ((ExtendedPrimitive) this.getValueSubclass().get(1)).tag);
            }
        }

        // badHashMap: validate keys by their id
        if (!this.getBadHashMap().isEmpty()) {
            for (Map.Entry<BadHashKey, String> e : this.getBadHashMap().entrySet()) {
                // find matching key in copy map by id
                boolean found = false;
                for (BadHashKey k : copy.getBadHashMap().keySet()) {
                    if (Objects.equals(k.id, e.getKey().id)) {
                        found = true;
                        break;
                    }
                }
                if (!found) Assertions.fail("badHashMap key missing after reload: " + e.getKey().id);
            }
        }
    }

    // helper subclass to test polymorphic values
    public static class ExtendedPrimitive extends PrimitiveTypes {
        public String tag;
    }

    // key type that returns constant hash code to force collisions
    public static class BadHashKey {
        public final Integer id;

        public BadHashKey(Integer id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BadHashKey that = (BadHashKey) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return 42;
        }

        @Override
        public String toString() {
            return "BadHashKey{" + id + '}';
        }
    }
}
