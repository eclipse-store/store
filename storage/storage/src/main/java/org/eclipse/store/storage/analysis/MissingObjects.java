package org.eclipse.store.storage.analysis;

/*-
 * #%L
 * EclipseStore Storage
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

import java.util.Set;

/**
 * Interface defining the result of searching for missing objects.
 */
public interface MissingObjects
{
	/**
	 * Get all missing IDs.
	 * 
	 * @return a Set containing all Ids of missing objects.
	 */
	Set<Long> getMissingObjectIDs();
	
	/**
	 * Provides the result of the missing objects search.
	 */
	class Default implements MissingObjects
	{
		private final Set<Long> missingObjectsIds;
	
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
	
		public Default(final Set<Long> missingObjectsIds)
		{
			super();
			this.missingObjectsIds = missingObjectsIds;
		}
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
	
		@Override
		public Set<Long> getMissingObjectIDs()
		{
			return this.missingObjectsIds;
		}
			
		@Override
		public String toString()
		{
			return "Default [missingObjectsIds=" + this.missingObjectsIds + "]";
		}
	}
}
