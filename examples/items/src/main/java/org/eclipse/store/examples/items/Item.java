
package org.eclipse.store.examples.items;

/*-
 * #%L
 * EclipseStore Example Items
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

import java.time.LocalDateTime;


public class Item
{
	private final String        title;
	private final LocalDateTime createdAt;
	
	public Item(
		final String title
	)
	{
		super();
		
		this.title     = title;
		this.createdAt = LocalDateTime.now();
	}
	
	public String getTitle()
	{
		return this.title;
	}
	
	public LocalDateTime getCreatedAt()
	{
		return this.createdAt;
	}
	
	@Override
	public String toString()
	{
		return this.title + " created at " + this.createdAt;
	}
}
