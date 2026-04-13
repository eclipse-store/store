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

/**
 * A customer review attached to a {@link Wine}.
 * <p>
 * Reviews are stored inside {@link Wine#getReviews()} (behind a lazy reference) and contribute to
 * the wine's aggregated {@link Wine#getRating() average rating}. A review records the reviewing
 * {@link Customer}, a numeric rating (typically 0.0 – 5.0), an optional free-form comment, and
 * the date it was submitted.
 */
public class Review
{
	private Customer      customer;
	private double        rating;
	private String        text;
	private LocalDateTime date;

	/**
	 * No-arg constructor required by EclipseStore for object reconstruction during loading.
	 */
	public Review()
	{
	}

	/**
	 * Creates a new review.
	 *
	 * @param customer the reviewing customer
	 * @param rating   the numeric rating (typically 0.0 – 5.0)
	 * @param text     the free-form review text (may be {@code null})
	 * @param date     the date the review was submitted
	 */
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

	/** @return the reviewing customer */
	public Customer getCustomer()
	{
		return this.customer;
	}

	/** @return the numeric rating (typically 0.0 – 5.0) */
	public double getRating()
	{
		return this.rating;
	}

	/** @return the free-form review text, or {@code null} if none */
	public String getText()
	{
		return this.text;
	}

	/** @return the submission date */
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
