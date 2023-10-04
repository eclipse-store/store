package org.eclipse.store.storage.restclient.types;

/*-
 * #%L
 * EclipseStore Storage REST Client
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

import java.util.List;

public interface StorageViewComplexRangeEntry extends StorageViewElement
{
	public static class Default extends StorageViewElement.Abstract implements StorageViewComplexRangeEntry
	{
		private final List<StorageViewElement> members;
		
		Default(
			final StorageView.Default view,
			final StorageViewElement parent,
			final String name,
			final String value,
			final List<StorageViewElement> members
		)
		{
			super(view, parent, name, value, null);
			
			this.members = members;
		}
		
		@Override
		public boolean hasMembers()
		{
			return this.members.size() > 0;
		}
		
		@Override
		public List<StorageViewElement> members(
			final boolean forceRefresh
		)
		{
			return this.members;
		}
		
	}
	
}
