package one.microstream.test.serializerstandalone.domain;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - serializer-standalone
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

public class Order
{
	private String   id;
	private Customer customer;

	public Order()
	{
	}

	public Order(final String id)
	{
		this.id = id;
	}

	public String id()
	{
		return this.id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public Customer customer()
	{
		return this.customer;
	}

	public void setCustomer(final Customer customer)
	{
		this.customer = customer;
	}
}
