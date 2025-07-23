package org.eclipse.store.gigamap.lucene;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
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

import com.github.javafaker.Faker;
import com.github.javafaker.Name;
import org.eclipse.store.gigamap.misc.it.Person;
import org.apache.lucene.document.Document;

import java.time.ZoneId;

public class GigaMapTestLucenePerson extends GigaMapTestBaseLucene<Person>
{
	public GigaMapTestLucenePerson()
	{
		super(10_000);
	}
	
	@Override
	protected DocumentPopulator<Person> createDocumentPopulator()
	{
		return new PersonDocumentPopulator();
	}
	
	@Override
	protected Person createEntity(final Faker faker, final long index)
	{
		final Name name = faker.name();
		return new Person(
			name.firstName(),
			name.lastName(),
			faker.date().birthday(1, 99).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
		);
	}
	
//	@Test
	void testQueries()
	{
		this.testQuery(
			"year=2000",
			"dobYear:2000"
		);
		
		this.testQuery(
			"firstname=John",
			"name:John"
		);
	}
	
	
	static class PersonDocumentPopulator extends DocumentPopulator<Person>
	{
		@Override
		public void populate(final Document document, final Person entity)
		{
			document.add(createIntField("dobYear", entity.dob().getYear()));
			document.add(createTextField("firstName", entity.firstName()));
			document.add(createTextField("lastName", entity.lastName()));
		}
	}
	
}
