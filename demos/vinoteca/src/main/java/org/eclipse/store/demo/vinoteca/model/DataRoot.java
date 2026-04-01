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

import org.eclipse.store.gigamap.types.GigaMap;

public class DataRoot
{
	private GigaMap<Wine>    wines;
	private GigaMap<Winery>  wineries;
	private List<Customer>   customers;
	private List<Order>      orders;

	public DataRoot()
	{
		this.wines     = GigaMap.New();
		this.wineries  = GigaMap.New();
		this.customers = new ArrayList<>();
		this.orders    = new ArrayList<>();
	}

	public GigaMap<Wine> getWines()
	{
		return this.wines;
	}

	public GigaMap<Winery> getWineries()
	{
		return this.wineries;
	}

	public List<Customer> getCustomers()
	{
		return this.customers;
	}

	public List<Order> getOrders()
	{
		return this.orders;
	}
}
