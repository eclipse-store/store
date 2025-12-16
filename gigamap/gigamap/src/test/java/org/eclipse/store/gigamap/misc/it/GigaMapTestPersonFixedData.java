package org.eclipse.store.gigamap.misc.it;

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

import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.IndexerLocalDate;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Year;
import java.util.HashSet;
import java.util.Set;

import net.datafaker.Faker;

public class GigaMapTestPersonFixedData extends GigaMapTestBaseBitmapIndex<Person>
{
	private final String[]               firstNames;
	private final String[]               lastNames;
	private final int                    dobYearRange   = 99;
	
	private final PersonFirstNameIndexer indexFirstName = new PersonFirstNameIndexer();
	private final PersonLastNameIndexer  indexLastName  = new PersonLastNameIndexer();
	private final PersonBirthYearIndexer indexBirthYear = new PersonBirthYearIndexer();
	
	public GigaMapTestPersonFixedData()
	{
		//super(10_000_000);
		super(1_000_000); //reduce data for faster tests

		final Faker faker = new Faker();
		final Set<String> firstNames = new HashSet<>();
		while(firstNames.size() < 125)
		{
			firstNames.add(faker.name().firstName());
		}
		this.firstNames = firstNames.toArray(String[]::new);
		final Set<String> lastNames = new HashSet<>();
		while(lastNames.size() < 40)
		{
			lastNames.add(faker.name().lastName());
		}
		this.lastNames = lastNames.toArray(String[]::new);
	}
	
	@Override
	protected void createIndices(final BitmapIndices<Person> indices)
	{
		indices.add(this.indexFirstName);
		indices.add(this.indexLastName);
		indices.add(this.indexBirthYear);
	}
	
	@Override
	protected Person createEntity(final Faker faker, final long index)
	{
		return new Person(
			this.firstNames[(int)(index % this.firstNames.length)],
			this.lastNames [(int)(index % this.lastNames .length)],
			LocalDate.of(Year.now().getValue() - (int)(index % this.dobYearRange), 1, 1)
		);
	}
	
	@Test
	void testQueryYear()
	{
		this.testQueryResultCount(
			this.gigaMap().query(this.indexBirthYear.isYear(Year.now().getValue() - this.dobYearRange / 2)),
			this.testDataAmount() / this.dobYearRange
		);
	}
	
	@Test
	void testQueryFirstName()
	{
		this.testQueryResultCount(
			this.gigaMap().query(this.indexFirstName.is(this.firstNames[0])),
			this.testDataAmount() / this.firstNames.length
		);
	}
	
	@Test
	void testQueryLastName()
	{
		this.testQueryResultCount(
			this.gigaMap().query(this.indexLastName.is(this.lastNames[0])),
			this.testDataAmount() / this.lastNames.length
		);
	}
	
//	@Test
//	void testQuery3()
//	{
//		final int    testYear      = 2000;
//		final String testFirstName = "John";
//		this.testQueryResultCount(
//			this.gigaMap().query(this.indexBirthYear.is(testYear).and(this.indexFirstName.is(testFirstName))),
//			p -> p.dob().getYear() == testYear && p.firstName().equals(testFirstName)
//		);
//	}
		
	
	static class PersonFirstNameIndexer extends IndexerString.Abstract<Person>
	{
		@Override
		protected String getString(Person entity)
		{
			return entity.firstName();
		}
	}
	
	
	static class PersonLastNameIndexer extends IndexerString.Abstract<Person>
	{
		@Override
		protected String getString(Person entity)
		{
			return entity.lastName();
		}
	}
	
	
	static class PersonBirthYearIndexer extends IndexerLocalDate.Abstract<Person>
	{
		@Override
		protected LocalDate getLocalDate(Person entity)
		{
			return entity.dob();
		}
	}
	
}
