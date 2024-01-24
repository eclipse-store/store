
package org.eclipse.store.examples.deleting;

/*-
 * #%L
 * microstream-examples-deleting
 * %%
 * Copyright (C) 2019 - 2022 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

public class MyData
{
	private String name;
	private int    intValue;
	
	public MyData(final String name, final int value)
	{
		super();
		this.name     = name;
		this.intValue = value;
	}
	
	public String getName()
	{
		return this.name;
	}
	
	public void setName(final String name)
	{
		this.name = name;
	}
	
	public int getIntegerValue()
	{
		return this.intValue;
	}
	
	public void setIntValue(final int integerValue)
	{
		this.intValue = integerValue;
	}
	
	@Override
	public String toString()
	{
		return this.name + " value: " + this.intValue;
	}
	
}
