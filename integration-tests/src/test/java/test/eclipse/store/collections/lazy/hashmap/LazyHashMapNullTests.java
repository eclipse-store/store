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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LazyHashMapNullTests {

	@Test
	void addNullKey() {
		final Map<String, String> lazyMap = new LazyHashMap<>();
		final Map<String, String> refMap = new HashMap<>();
		
		final String refResult =refMap.put(null, "value of null key");
		final String lazyResult = lazyMap.put(null, "value of null key");
				
		assertEquals(refResult, lazyResult);
	}
	
	@Test
	void addNullKeyTwice() {
		final Map<String, String> lazyMap = new LazyHashMap<>();
		final Map<String, String> refMap = new HashMap<>();
		
		refMap.put(null, "value of null key");
		lazyMap.put(null, "value of null key");
					
		final String refResult =refMap.put(null, "added again null key");
		final String lazyResult = lazyMap.put(null, "added again null key");
		
		assertEquals(refResult, lazyResult);
	}
	
	@Test
	void containsNullKey() {
		final Map<String, String> lazyMap = new LazyHashMap<>();
		final Map<String, String> refMap = new HashMap<>();
		
		refMap.put(null, "value of null key");
		lazyMap.put(null, "value of null key");
						
		final boolean refResult = refMap.containsKey(null);
		final boolean lazyResult = lazyMap.containsKey(null);
				
		assertEquals(refResult, lazyResult);
	}
	
	@Test
	void containsNoNullKey() {
		final Map<String, String> lazyMap = new LazyHashMap<>();
		final Map<String, String> refMap = new HashMap<>();
		
		refMap.put("key a", "value of null key");
		lazyMap.put("key a", "value of null key");
						
		final boolean refResult = refMap.containsKey(null);
		final boolean lazyResult = lazyMap.containsKey(null);
				
		assertEquals(refResult, lazyResult);
	}
	
	@Test
	void getNullKey() {
		final Map<String, String> lazyMap = new LazyHashMap<>();
		final Map<String, String> refMap = new HashMap<>();
		
		refMap.put(null, "value of null key");
		lazyMap.put(null, "value of null key");
						
		final String refResult = refMap.get(null);
		final String lazyResult = lazyMap.get(null);
				
		assertEquals(refResult, lazyResult);
	}
	
	@Test
	void getNoNullKey() {
		final Map<String, String> lazyMap = new LazyHashMap<>();
		final Map<String, String> refMap = new HashMap<>();
		
		refMap.put("key a", "value of null key");
		lazyMap.put("key a", "value of null key");
						
		final String refResult = refMap.get(null);
		final String lazyResult = lazyMap.get(null);
				
		assertEquals(refResult, lazyResult);
	}
	
	@Test
	void removeNullKey() {
		final Map<String, String> lazyMap = new LazyHashMap<>();
		final Map<String, String> refMap = new HashMap<>();
		
		refMap.put(null, "value of null key");
		lazyMap.put(null, "value of null key");
						
		final String refResult = refMap.remove(null);
		final String lazyResult = lazyMap.remove(null);
				
		assertEquals(refResult, lazyResult);
	}
	
	@Test
	void removeNoNullKey() {
		final Map<String, String> lazyMap = new LazyHashMap<>();
		final Map<String, String> refMap = new HashMap<>();
		
		refMap.put("key a", "value of null key");
		lazyMap.put("key a", "value of null key");
						
		final String refResult = refMap.remove(null);
		final String lazyResult = lazyMap.remove(null);
				
		assertEquals(refResult, lazyResult);
	}

	
	
	@Test
	void addNullValue() {
		final Map<String, String> lazyMap = new LazyHashMap<>();
		final Map<String, String> refMap = new HashMap<>();
		
		final String refResult =refMap.put("key a", null);
		final String lazyResult = lazyMap.put("key a", null);
				
		assertEquals(refResult, lazyResult);
	}
	
	@Test
	void addNullValueTwice() {
		final Map<String, String> lazyMap = new LazyHashMap<>();
		final Map<String, String> refMap = new HashMap<>();
		
		refMap.put("key a", null);
		lazyMap.put("key a", null);
					
		final String refResult =refMap.put("key a", null);
		final String lazyResult = lazyMap.put("key a", null);
		
		assertEquals(refResult, lazyResult);
	}
	
	@Test
	void getNullValue() {
		final Map<String, String> lazyMap = new LazyHashMap<>();
		final Map<String, String> refMap = new HashMap<>();
		
		refMap.put("key a", null);
		lazyMap.put("key a", null);
						
		final String refResult = refMap.get(null);
		final String lazyResult = lazyMap.get(null);
				
		assertEquals(refResult, lazyResult);
	}
	
	@Test
	void removeNullValue() {
		final Map<String, String> lazyMap = new LazyHashMap<>();
		final Map<String, String> refMap = new HashMap<>();
		
		refMap.put("key a", null);
		lazyMap.put("key a", null);
						
		final boolean refResult = refMap.remove("key a", null);
		final boolean lazyResult = lazyMap.remove("key a", null);
				
		assertEquals(refResult, lazyResult);
	}
	
	@Test
	void ReplaceNullKey() {
		final Map<String, String> lazyMap = new LazyHashMap<>();
		final Map<String, String> refMap = new HashMap<>();
		
		refMap.put(null, "value of null key");
		lazyMap.put(null, "value of null key");
						
		final String refResultReplace = refMap.replace(null, "replaced");
		final String lazyResultReplace = lazyMap.replace(null, "replaced");
				
		assertEquals(refResultReplace, lazyResultReplace);
		
		final String refResult = refMap.get(null);
		final String lazyResult = lazyMap.get(null);
				
		assertEquals(refResult, lazyResult);
	}
}
