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

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LazyHashMapSpliteratorTest {

	@Test
	void StreamParallelKeySet() {
		final int numElements = 100;
		
		final LazyHashMap<String, String> map = new LazyHashMap<>();
		
		for(int i = 0; i < numElements; i++) {
			map.put("Key " + i, "Entry " + i);
		}
		
		final long count = map.keySet()
				.parallelStream()
				.filter(e->e.contains("9"))
				.count();
		assertEquals(19,  count);
	}
	
	@Test
	void StreamParallelValuesSet() {
		final int numElements = 100;
		
		final LazyHashMap<String, String> map = new LazyHashMap<>();
		
		for(int i = 0; i < numElements; i++) {
			map.put("Key " + i, "Entry " + i);
		}
		
		final long count = map.values()
				.parallelStream()
				.filter(e->e.contains("9"))
				.count();
		assertEquals(19,  count);
	}
	
	@Test
	void StreamParallelEntrySet() {
		final int numElements = 100;
		
		final LazyHashMap<String, String> map = new LazyHashMap<>();
		
		for(int i = 0; i < numElements; i++) {
			map.put("Key " + i, "Entry " + i);
		}
		
		final long count = map.entrySet()
				.parallelStream()
				.filter(e->e.getKey().contains("9"))
				.count();
		assertEquals(19,  count);
	}
	
	@Test
	void StreamKeySet() {
		final int numElements = 100;
		
		final LazyHashMap<String, String> map = new LazyHashMap<>();
		
		for(int i = 0; i < numElements; i++) {
			map.put("Key " + i, "Entry " + i);
		}
		
		final long count = map.keySet()
				.stream()
				.filter(e->e.contains("9"))
				.count();
		assertEquals(19,  count);
	}
	
	@Test
	void StreamValueSet() {
		final int numElements = 100;
		
		final LazyHashMap<String, String> map = new LazyHashMap<>();
		
		for(int i = 0; i < numElements; i++) {
			map.put("Key " + i, "Entry " + i);
		}
		
		final long count = map.values()
				.stream()
				.filter(e->e.contains("9"))
				.count();
		assertEquals(19,  count);
	}
	
	@Test
	void StreamEntrySet() {
		final int numElements = 100;
		
		final LazyHashMap<String, String> map = new LazyHashMap<>();
		
		for(int i = 0; i < numElements; i++) {
			map.put("Key " + i, "Entry " + i);
		}
		
		final long count = map.entrySet()
				.stream()
				.filter(e->e.getKey().contains("9"))
				.count();
		assertEquals(19,  count);
	}
	
	@Test
	void createSpliteratorKeySetRecursive() {
				
		final int numElements = 100;
		
		final LazyHashMap<String, String> map = new LazyHashMap<>();
		
		for(int i = 0; i < numElements; i++) {
			map.put("Key " + i, "Entry " + i);
		}
			
		final List<Spliterator<?>> spliterators = this.splitAll(map.keySet().spliterator());
		
		for (final Spliterator<?> spliterator : spliterators) {
			while(spliterator.tryAdvance(e -> e.toString())) {
				//noop
				}
		}
	}
	
	@Test
	void createSpliteratorValueSetRecursive() {
				
		final int numElements = 2000;
		
		final LazyHashMap<String, String> map = new LazyHashMap<>();
		
		for(int i = 0; i < numElements; i++) {
			map.put("Key " + i, "Entry " + i);
		}
			
		final List<Spliterator<?>> spliterators = this.splitAll(map.values().spliterator());
			
		assertTrue(spliterators.size() > 1);
	}
	
	@Test
	void createSpliteratorEntrySetRecursive() {
				
		final int numElements = 2000;
		
		final LazyHashMap<String, String> map = new LazyHashMap<>();
		
		for(int i = 0; i < numElements; i++) {
			map.put("Key " + i, "Entry " + i);
		}
			
		final List<Spliterator<?>> spliterators = this.splitAll(map.entrySet().spliterator());
			
		assertTrue(spliterators.size() > 1);
	}
	
	@Test
	void StreamEmptyKeySet() {	
		final LazyHashMap<String, String> map = new LazyHashMap<>();
		
		final long count = map.keySet()
				.stream()
				.filter(e->e.contains("9"))
				.count();
		assertEquals(0,  count);
	}
	
	@Test
	void StreamEmptyValueSet() {
		final LazyHashMap<String, String> map = new LazyHashMap<>();
		
		final long count = map.values()
				.stream()
				.filter(e->e.contains("9"))
				.count();
		assertEquals(0,  count);
	}
	
	@Test
	void StreamEmptyEntrySet() {
		final LazyHashMap<String, String> map = new LazyHashMap<>();
				
		final long count = map.entrySet()
				.stream()
				.filter(e->e.getKey().contains("9"))
				.count();
		assertEquals(0,  count);
	}
	
	private List<Spliterator<?>> splitAll(final Spliterator<?> spliterator) {
		final List<Spliterator<?>> spliterators = new ArrayList<>();
		final Spliterator<?> split = spliterator.trySplit();
		
		if(split != null) {
			spliterators.addAll(this.splitAll(spliterator));
			spliterators.addAll(this.splitAll(split));
		} else {
			spliterators.add(spliterator);
		}
		
		return spliterators;
	}

	// java > 16 test
//	@Test
//	void emptySpliteratorTest()
//	{
//		LazyHashMap<String, String> stringStringLazyHashMap = new LazyHashMap<>();
//		stringStringLazyHashMap.values().stream().toList();
//	}
}
