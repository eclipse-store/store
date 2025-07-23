package org.eclipse.store.gigamap.indexer.annotation.custom;

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

import java.util.Objects;


public class CustomType
{
	private final String value;
	
	public CustomType(final String value)
	{
		this.value = value;
	}
	
	public String value()
	{
		return this.value;
	}
	
	@Override
	public boolean equals(Object o)
	{
		if(!(o instanceof CustomType))
			return false;
		CustomType that = (CustomType)o;
		return Objects.equals(this.value, that.value);
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hashCode(value);
	}
	
}
