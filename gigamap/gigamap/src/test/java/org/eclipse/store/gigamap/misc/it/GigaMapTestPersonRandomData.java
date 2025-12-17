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
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import net.datafaker.Faker;
import net.datafaker.providers.base.Name;

public class GigaMapTestPersonRandomData extends GigaMapTestBaseBitmapIndex<Person>
{
	private final PersonFirstNameIndexer indexFirstName = new PersonFirstNameIndexer();
	private final PersonLastNameIndexer  indexLastName  = new PersonLastNameIndexer();
	private final PersonBirthYearIndexer indexBirthYear = new PersonBirthYearIndexer();
	
	public GigaMapTestPersonRandomData()
	{
		//super(10_000_000);
		super(1_000_000); //reduce data for faster tests
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
		final Name name = faker.name();
		return new Person(
			name.firstName(),
			name.lastName(),
			faker.timeAndDate().birthday(1, 99)
		);
	}
	
	@Test
	void testQueryYear()
	{
		final int testYear = 2000;
		this.testQueryResultCount(
			this.gigaMap().query(this.indexBirthYear.is(testYear)),
			p -> p.dob().getYear() == testYear
		);
	}
	
	@Test
	void testQueryFirstName()
	{
		final String testName = "John";
		this.testQueryResultCount(
			this.gigaMap().query(this.indexFirstName.is(testName)),
			p -> p.firstName().equals(testName)
		);
	}
	
	@Test
	void testQueryLastName()
	{
		final String testName = "Smith";
		this.testQueryResultCount(
			this.gigaMap().query(this.indexLastName.is(testName)),
			p -> p.lastName().equals(testName)
		);
	}
		
	
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
	
	
	static class PersonBirthYearIndexer extends IndexerInteger.Abstract<Person>
	{
		@Override
		protected Integer getInteger(Person entity)
		{
			return entity.dob().getYear();
		}
	}
	
}
