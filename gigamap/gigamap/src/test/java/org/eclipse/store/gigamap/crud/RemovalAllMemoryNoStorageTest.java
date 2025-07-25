package org.eclipse.store.gigamap.crud;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.types.BinaryIndexerUUID;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RemovalAllMemoryNoStorageTest
{
	
	static final int CUSTOMER_COUNT = 20_000;
	static final int ITERATIONS = 10;
	
	static MemoryStatusCollector noIndexer    = new MemoryStatusCollector();
	static MemoryStatusCollector oneIndexer   = new MemoryStatusCollector();
	static MemoryStatusCollector twoIndexer   = new MemoryStatusCollector();
	static MemoryStatusCollector threeIndexer = new MemoryStatusCollector();

	
	@AfterAll
	static void printResults() {
		for (int i = 0; i < ITERATIONS; i++) {
			System.out.printf("# %d%n", i + 1);
			final String format = "| %-20s | %-15s | %-15s | %-15s | %-15s |%n";
			System.out.printf(format, "",
				"No indexer", "One indexer", "Two indexer", "Three indexer");
			
			System.out.printf(format,
				"Before fill",
				getBeforeFill(noIndexer.list, i),
				getBeforeFill(oneIndexer.list, i),
				getBeforeFill(twoIndexer.list, i),
				getBeforeFill(threeIndexer.list, i));
			
			System.out.printf(format,
				"After fill",
				getAfterFill(noIndexer.list, i),
				getAfterFill(oneIndexer.list, i),
				getAfterFill(twoIndexer.list, i),
				getAfterFill(threeIndexer.list, i));
			
			System.out.printf(format,
				"After remove",
				getAfterRemove(noIndexer.list, i),
				getAfterRemove(oneIndexer.list, i),
				getAfterRemove(twoIndexer.list, i),
				getAfterRemove(threeIndexer.list, i));
			
			System.out.println("-----------------------------------------------------------------------------------------");
		}
		
		System.gc();

		System.out.println("After all: " + memoryUsage() + " MB");
		
		System.out.println("-----------------------------------------------------------------------------------------");
	}
	
	private static String getBeforeFill(final List<MemoryStatus> list, final int index) {
		return index < list.size() ? list.get(index).getBeforeFill() + " MB" : "";
	}
	
	private static String getAfterFill(final List<MemoryStatus> list, final int index) {
		return index < list.size() ? list.get(index).getAfterFill() + " MB" : "";
	}
	
	private static String getAfterRemove(final List<MemoryStatus> list, final int index) {
		return index < list.size() ? list.get(index).getAfterRemove() + " MB"  : "";
	}
	
	@Test
	@Order(0)
	void testNoIndexer()
	{
		System.out.println("Memory usage: " + memoryUsage() + " MB");
		
		final GigaMap<Customer> gigaMap = GigaMap.New();
		
		this.runTest(gigaMap, noIndexer);
	}
	
	@Test
	@Order(1)
	void testOneIndexer()
	{
		System.out.println("Memory usage: " + memoryUsage() + " MB");
		
		final GigaMap<Customer> gigaMap = GigaMap.New();
		final NameIndexer nameIndexer = new NameIndexer();
		gigaMap.index().bitmap().add(nameIndexer);
		
		this.runTest(gigaMap, oneIndexer);
	}
	
	@Test
	@Order(2)
	void testTwoIndexer()
	{
		System.out.println("Memory usage: " + memoryUsage() + " MB");
		
		final GigaMap<Customer> gigaMap = GigaMap.New();
		final NameIndexer nameIndexer = new NameIndexer();
		final UUIDIndexer uuidIndexer = new UUIDIndexer();
		gigaMap.index().bitmap().add(nameIndexer);
		gigaMap.index().bitmap().add(uuidIndexer);
		
		this.runTest(gigaMap, twoIndexer);
	}
	
	@Test
	@Order(3)
	void testThreeIndexer()
	{
		System.out.println("Memory usage: " + memoryUsage() + " MB");
		
		final GigaMap<Customer> gigaMap = GigaMap.New();
		final NameIndexer nameIndexer = new NameIndexer();
		final UUIDIndexer uuidIndexer = new UUIDIndexer();
		final AgeIndexer ageIndexer = new AgeIndexer();
		gigaMap.index().bitmap().add(nameIndexer);
		gigaMap.index().bitmap().add(uuidIndexer);
		gigaMap.index().bitmap().add(ageIndexer);
		
		this.runTest(gigaMap, threeIndexer);
	}
	
	private void runTest(final GigaMap<Customer> gigaMap, final MemoryStatusCollector collector)
	{
		for (int j = 0; j < ITERATIONS; j++) {
			
			final long beforeFill = memoryUsage();
			
			gigaMap.addAll(createCustomers());
			
			final long afterStore = memoryUsage();
			
			gigaMap.removeAll();
			
			System.gc();
			
			final long afterRemove = memoryUsage();
			
			collector.add(
				new MemoryStatus(beforeFill, afterStore, afterRemove)
			);
		}
	}
	
	private static List<Customer> createCustomers()
	{
		final List<Customer> customers = new ArrayList<>(CUSTOMER_COUNT);
		for (int i = 0; i < CUSTOMER_COUNT; i++) {
			customers.add(new Customer(UUID.randomUUID(), "Doe" + (i % 100), 50 + i));
		}
		return customers;
	}
	
	static long memoryUsage()
	{
		return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())  / 1024 / 1024;
	}
	
	
	static class NameIndexer extends IndexerString.Abstract<Customer>
	{
		@Override
		protected String getString(final Customer entity)
		{
			return entity.getName();
		}
	}
	
	static class AgeIndexer extends IndexerInteger.Abstract<Customer>
	{
		@Override
		protected Integer getInteger(final Customer entity)
		{
			return entity.getAge();
		}
	}
	
	static class UUIDIndexer extends BinaryIndexerUUID.Abstract<Customer>
	{
		@Override
		protected UUID getUUID(final Customer entity)
		{
			return entity.uid;
		}
	}
	
	static class Customer
	{
		private final String name;
		private final int age;
		private final UUID uid;
		
		public Customer(final UUID uuid, final String name, final int age)
		{
			this.uid = uuid;
			this.name = name;
			this.age = age;
		}
		
		public String getName()
		{
			return this.name;
		}
		
		public int getAge()
		{
			return this.age;
		}
	}
	
	static class MemoryStatus {
		long beforeFill;
		long afterFill;
		long afterRemove;
		
		public MemoryStatus(final long beforeFill, final long afterFill, final long afterRemove)
		{
			this.beforeFill = beforeFill;
			this.afterFill = afterFill;
			this.afterRemove = afterRemove;
		}
		
		public long getBeforeFill()
		{
			return this.beforeFill;
		}
		
		public long getAfterFill()
		{
			return this.afterFill;
		}
		
		public long getAfterRemove()
		{
			return this.afterRemove;
		}
	}
	
	static class MemoryStatusCollector
	{
		List<MemoryStatus> list = new ArrayList<>();
		
		public void add(final MemoryStatus status)
		{
			this.list.add(status);
		}
		
	}
	
}
