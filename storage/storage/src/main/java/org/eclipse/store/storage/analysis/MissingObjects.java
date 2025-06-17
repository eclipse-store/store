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

import java.util.Map;
import java.util.Set;

/**
 * Interface defining the result of searching for missing objects.
 */
public interface MissingObjects
{
	/**
	 * Get the chain of references from root to the object
	 * associated with the supplied id.
	 * The returned array starts with the root object id and
	 * ends with the missing object id.
	 * This information is not guaranteed to exist.
	 * 
	 * @param objectID as long.
	 * @return an array of object IDs.
	 */
	long[] getParents(long objectID);

	/**
	 * Get all missing IDs.
	 * 
	 * @return a Set containing all Ids of missing objects.
	 */
	Set<Long> getMissingObjectIDs();
	
	/**
	 * Helper class that provides the result of the missing objects search.
	 */
	class Default implements MissingObjects
	{
		private final Map<Long, long[]> parents;
		private final Set<Long> missingObjectsIds;
	
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
	
		public Default(final Set<Long> missingObjectsIds, final Map<Long, long[]> parents)
		{
			super();
			this.missingObjectsIds = missingObjectsIds;
			this.parents = parents;
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
		public long[] getParents(final long objectID)
		{
			return this.parents.get(objectID);
		}
	
		@Override
		public String toString()
		{
			return "Default [missingObjectsPaths=" + this.parents + "]";
		}
	}
}
