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

import java.time.LocalDateTime;

public class Review
{
	private Customer      customer;
	private double        rating;
	private String        text;
	private LocalDateTime date;

	public Review()
	{
	}

	public Review(
		final Customer      customer,
		final double        rating,
		final String        text,
		final LocalDateTime date
	)
	{
		this.customer = customer;
		this.rating   = rating;
		this.text     = text;
		this.date     = date;
	}

	public Customer getCustomer()
	{
		return this.customer;
	}

	public double getRating()
	{
		return this.rating;
	}

	public String getText()
	{
		return this.text;
	}

	public LocalDateTime getDate()
	{
		return this.date;
	}

	@Override
	public String toString()
	{
		return "Review[" + this.customer.getFullName() + ", " + this.rating + "]";
	}
}
