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

/**
 * Domain entity representing a Vinoteca customer.
 * <p>
 * A {@code Customer} aggregates personal contact information together with the customer's
 * order history. The order list is held behind a {@link org.eclipse.serializer.reference.Lazy Lazy}
 * reference so that browsing or editing customer master data does not pull a potentially long
 * order history into memory.
 *
 * @see Order
 */
public class Customer
{
	private String firstName;
	private String lastName;
	private String email;
	private String city;
	private String country;
	private Lazy<List<Order>> orders;

	/**
	 * No-arg constructor required by EclipseStore for object reconstruction during loading.
	 * Initializes an empty, lazily held order list.
	 */
	public Customer()
	{
		this.orders = Lazy.Reference(new ArrayList<>());
	}

	/**
	 * Creates a fully populated {@code Customer} with an empty order history.
	 *
	 * @param firstName the customer's given name
	 * @param lastName  the customer's family name
	 * @param email     the customer's email address (assumed unique within the dataset)
	 * @param city      the customer's city (may be {@code null})
	 * @param country   the customer's country (may be {@code null})
	 */
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

	/** @return the customer's given name */
	public String getFirstName()
	{
		return this.firstName;
	}

	/** @param firstName the new given name */
	public void setFirstName(final String firstName)
	{
		this.firstName = firstName;
	}

	/** @return the customer's family name */
	public String getLastName()
	{
		return this.lastName;
	}

	/** @param lastName the new family name */
	public void setLastName(final String lastName)
	{
		this.lastName = lastName;
	}

	/** @return the customer's email address */
	public String getEmail()
	{
		return this.email;
	}

	/** @param email the new email address */
	public void setEmail(final String email)
	{
		this.email = email;
	}

	/** @return the customer's city, or {@code null} if unknown */
	public String getCity()
	{
		return this.city;
	}

	/** @param city the new city (may be {@code null}) */
	public void setCity(final String city)
	{
		this.city = city;
	}

	/** @return the customer's country, or {@code null} if unknown */
	public String getCountry()
	{
		return this.country;
	}

	/** @param country the new country (may be {@code null}) */
	public void setCountry(final String country)
	{
		this.country = country;
	}

	/**
	 * Returns the customer's order history, loading the lazy reference if necessary.
	 *
	 * @return the (possibly empty) order list
	 */
	public List<Order> getOrders()
	{
		return Lazy.get(this.orders);
	}

	/**
	 * Returns the underlying lazy reference itself, useful when callers want to control loading
	 * (for example to call {@link Lazy#clear()}) instead of forcing the wrapped value to load.
	 *
	 * @return the lazy reference holding the order list
	 */
	public Lazy<List<Order>> orders()
	{
		return this.orders;
	}

	/**
	 * Convenience helper that joins {@link #getFirstName() first} and {@link #getLastName() last}
	 * name with a single space.
	 *
	 * @return the full display name
	 */
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
