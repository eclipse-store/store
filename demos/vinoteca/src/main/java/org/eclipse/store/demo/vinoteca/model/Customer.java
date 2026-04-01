package org.eclipse.store.demo.vinoteca.model;

/*-
 * #%L
 * EclipseStore Demo Vinoteca
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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.serializer.reference.Lazy;

public class Customer
{
	private String firstName;
	private String lastName;
	private String email;
	private String city;
	private String country;
	private Lazy<List<Order>> orders;

	public Customer()
	{
		this.orders = Lazy.Reference(new ArrayList<>());
	}

	public Customer(
		final String firstName,
		final String lastName,
		final String email,
		final String city,
		final String country
	)
	{
		this.firstName = firstName;
		this.lastName  = lastName;
		this.email     = email;
		this.city      = city;
		this.country   = country;
		this.orders    = Lazy.Reference(new ArrayList<>());
	}

	public String getFirstName()
	{
		return this.firstName;
	}

	public void setFirstName(final String firstName)
	{
		this.firstName = firstName;
	}

	public String getLastName()
	{
		return this.lastName;
	}

	public void setLastName(final String lastName)
	{
		this.lastName = lastName;
	}

	public String getEmail()
	{
		return this.email;
	}

	public void setEmail(final String email)
	{
		this.email = email;
	}

	public String getCity()
	{
		return this.city;
	}

	public void setCity(final String city)
	{
		this.city = city;
	}

	public String getCountry()
	{
		return this.country;
	}

	public void setCountry(final String country)
	{
		this.country = country;
	}

	public List<Order> getOrders()
	{
		return Lazy.get(this.orders);
	}

	public Lazy<List<Order>> orders()
	{
		return this.orders;
	}

	public String getFullName()
	{
		return this.firstName + " " + this.lastName;
	}

	@Override
	public String toString()
	{
		return "Customer[" + this.getFullName() + ", " + this.email + "]";
	}
}
