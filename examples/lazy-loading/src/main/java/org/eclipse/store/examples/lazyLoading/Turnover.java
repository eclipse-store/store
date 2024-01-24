package org.eclipse.store.examples.lazyLoading;

/*-
 * #%L
 * EclipseStore Example Lazy Loading
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.time.Instant;


public class Turnover
{
	private final double  amount;
	private final Instant timestamp;
	
	public Turnover(final double amount, final Instant timestamp)
	{
		super();
		this.amount    = amount;
		this.timestamp = timestamp;
	}
	
	public double getAmount()
	{
		return this.amount;
	}

	public Instant getTimestamp()
	{
		return this.timestamp;
	}
}
