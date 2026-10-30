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

import java.util.ArrayList;
import java.util.List;

public class Customer
{
	private String      email;
	private List<Order> orders;

	public Customer()
	{
		this.orders = new ArrayList<>();
	}

	public Customer(final String email)
	{
		this.email  = email;
		this.orders = new ArrayList<>();
	}

	public String email()
	{
		return this.email;
	}

	public void setEmail(final String email)
	{
		this.email = email;
	}

	public List<Order> orders()
	{
		return this.orders;
	}

	public void setOrders(final List<Order> orders)
	{
		this.orders = orders;
	}
}
