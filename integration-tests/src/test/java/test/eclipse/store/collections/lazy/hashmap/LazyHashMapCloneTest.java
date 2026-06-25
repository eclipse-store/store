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

import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class LazyHashMapCloneTest {

	@Test
	void cloneMap() throws CloneNotSupportedException {
		final LazyHashMap<String, String> map = new LazyHashMap<>(17);
		
		for(int i = 0; i < 100; i++) {
			map.put("key " + i, "Value " + i);
		}
		
		final LazyHashMap<String, String> clonedMap = new LazyHashMap<>(map);
		
		assertNotEquals(clonedMap, map);
	}
}
