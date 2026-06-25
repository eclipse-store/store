package test.eclipse.store.collections.lazy.arraylist;

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

import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LazyArrayListIteratorTest {

	private static Class<?> clazz;

	@BeforeAll
	static void init() {
		final Class<?>[] declaredClass = LazyArrayList.class.getDeclaredClasses();
		final Optional<Class<?>> opt = Stream.of(declaredClass).filter( e -> e.getName().equals("one.microstream.collections.lazy.LazyArrayList$Itr") ).findFirst();
		if(opt.isPresent()) {
			clazz = opt.get();
		}
	}
	
	
	static void main(final String[] args) {
		
		final int numElements = 255000;
		final List<ListEntry> list = LazyArrayListPersistenceTest.createLazyList(10, numElements);
	}
	
	
	@Test
	@Disabled("to long test")
	void iterateNext() {
		
		final int numElements = 255000;
		final LazyArrayList<ListEntry> lazyList = LazyArrayListPersistenceTest.createLazyList(10,numElements);
		final Iterator<ListEntry> iter = lazyList.iterator();
		
		//assertInstanceOf(clazz, iter);
		

		int counter = 0;
		while(iter.hasNext()) {
			final ListEntry e = iter.next();
			assertEquals("Entry-" + counter, e.id);
			counter++;
		}
		assertEquals(numElements,counter);
	}
	
}
