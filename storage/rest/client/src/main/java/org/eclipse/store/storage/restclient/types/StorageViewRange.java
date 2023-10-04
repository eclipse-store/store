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

public interface StorageViewRange extends StorageViewElement
{
	public long offset();
	
	public long length();
	
	
	public static class Default extends StorageViewElement.Abstract implements StorageViewRange
	{
		private final long               objectId;
		private final long               offset;
		private final long               length;
		private List<StorageViewElement> members;
		
		Default(
			final StorageView.Default view,
			final StorageViewElement parent,
			final String name,
			final long objectId,
			final long offset,
			final long length
		)
		{
			super(view, parent, name, null, null);
			this.objectId = objectId;
			this.offset   = offset;
			this.length   = length;
		}

		@Override
		public long offset()
		{
			return this.offset;
		}

		@Override
		public long length()
		{
			return this.length;
		}
		
		@Override
		public boolean hasMembers()
		{
			return true;
		}
		
		@Override
		public List<StorageViewElement> members(final boolean forceRefresh)
		{
			if(this.members == null || forceRefresh)
			{
				this.members = this.view().variableMembers(
					this,
					this.objectId,
					this.offset,
					this.length
				);
			}
			return this.members;
		}
		
	}
	
}
