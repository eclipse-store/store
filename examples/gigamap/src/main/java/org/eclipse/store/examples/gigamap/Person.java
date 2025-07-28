package org.eclipse.store.examples.gigamap;

/*-
 * #%L
 * EclipseStore Example GigaMap
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.github.javafaker.Faker;

public class Person
{
	private final long           id         ;
	private final String         firstName  ;
	private       String         lastName   ;
	private final LocalDate      dateOfBirth;
	private       Address        address    ;
	private       List<Interest> interests  ;
	
	
	public Person(final long id, final Faker faker)
	{
		this(
			id,
			faker.name().firstName(),
			faker.name().lastName(),
			LocalDate.ofInstant(faker.date().birthday().toInstant(), ZoneId.systemDefault()),
			new Address(faker),
			Interest.random(faker.random())
		);
		
	}
	
	public Person(
		final long           id,
		final String         firstName,
		final String         lastName,
		final LocalDate      dateOfBirth,
		final Address        address,
		final List<Interest> interests
	)
	{
		super();
		this.id          = id;
		this.firstName   = firstName;
		this.lastName    = lastName;
		this.dateOfBirth = dateOfBirth;
		this.address     = address;
		this.interests   = interests;
	}
	
	public long getId()
	{
		return this.id;
	}

	public String getFirstName()
	{
		return this.firstName;
	}

	public String getLastName()
	{
		return this.lastName;
	}
	
	public void setLastName(final String lastName)
	{
		this.lastName = lastName;
	}

	public LocalDate getDateOfBirth()
	{
		return this.dateOfBirth;
	}
	
	public Address getAddress()
	{
		return this.address;
	}
	
	public void setAddress(final Address address)
	{
		this.address = address;
	}
	
	public List<Interest> getInterests()
	{
		return this.interests;
	}
	
	public void setInterests(final List<Interest> interests)
	{
		this.interests = interests;
	}

	@Override
	public String toString()
	{
		return "Person [id=" + this.id + ", firstName=" + this.firstName + ", lastName=" + this.lastName + ", dateOfBirth="
			+ this.dateOfBirth + ", address=" + this.address + ", interests=" + this.interests + "]";
	}

}
