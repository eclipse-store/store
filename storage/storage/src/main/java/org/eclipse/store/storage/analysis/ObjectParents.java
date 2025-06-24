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

/**
 * Interface defining the result of reverse object search.
 */
public interface ObjectParents
{
	/**
	 * Get the chain of references from root to the object
	 * associated with the supplied id.
	 * The returned array starts with the root object id and
	 * ends with the missing object id.
	 * 
	 * @param objectID as long.
	 * @return an array of object IDs.
	 */
	long[] getParents(long objectID);
	
	/**
	 * Provides the result of the reverse object search.
	 */
	public class Default implements ObjectParents
	{
		private final Map<Long, long[]> parents;

		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		public Default(final Map<Long, long[]> parents)
		{
			super();
			this.parents = parents;
		}
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public long[] getParents(final long objectID)
		{
			return this.parents.get(objectID);
		}

		@Override
		public String toString()
		{
			return "Default [parents=" + this.parents + "]";
		}
		
	}
}
