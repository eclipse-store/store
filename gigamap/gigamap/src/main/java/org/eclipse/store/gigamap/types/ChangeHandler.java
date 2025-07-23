package org.eclipse.store.gigamap.types;

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

/**
 * Represents a generic handler for tracking changes within an index. This interface
 * defines methods that manage entity changes, removal, and comparison of change states.
 */
public interface ChangeHandler
{
	/**
	 * Compares this ChangeHandler to another and determines if they are equal based on
	 * their state or characteristics.
	 *
	 * @param other the other ChangeHandler to compare with this instance
	 * @return true if the two ChangeHandlers are considered equal, otherwise false
	 */
	public boolean isEqual(ChangeHandler other);
	
	/**
	 * Removes the entity with the specified ID from the index.
	 *
	 * @param entityId the unique identifier of the entity to be removed from the index
	 */
	public void removeFromIndex(long entityId);
	
	/**
	 * Handles the changes in the index for a specific entity by updating its state with
	 * reference to the previous handler.
	 *
	 * @param entityId The unique identifier of the entity whose index change is being handled.
	 * @param prevEntityHandler The previous handler associated with the entity, used to
	 *                           manage transition or comparison during the change process.
	 */
	public void changeInIndex(long entityId, ChangeHandler prevEntityHandler);
}
