package org.eclipse.store.gigamap.misc.it;

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

public final class Counter
{
	private long count = 0L;
	
	public Counter()
	{
		super();
	}
	
	public void increment()
	{
		this.count++;
	}
	
	public void increment(final long diff)
	{
		this.count += diff;
	}
	
	public long get()
	{
		return this.count;
	}
	
}
