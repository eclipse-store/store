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

import java.time.LocalDate;

public class Person
{
	private final String    firstName;
	private final String    lastName;
	private final LocalDate dob;
	
	public Person(final String firstName, final String lastName, final LocalDate dob)
	{
		super();
		this.firstName = firstName;
		this.lastName  = lastName;
		this.dob       = dob;
	}
	
	public String firstName()
	{
		return this.firstName;
	}
	
	public String lastName()
	{
		return this.lastName;
	}
	
	public LocalDate dob()
	{
		return this.dob;
	}
	
	@Override
	public String toString()
	{
		return "Person [firstName=" + this.firstName + ", lastName=" + this.lastName + ", dob=" + this.dob + "]";
	}
	
}
