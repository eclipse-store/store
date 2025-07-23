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
 * A special implementation of the {@link ChangeHandler} interface that represents a no-operation
 * handler for changes in an index. This handler is typically used as a placeholder and does not
 * perform any actual operations, except for enforcing the removal of entities when required.
 */
public final class NullChangeChandler implements ChangeHandler
{
	static final NullChangeChandler SINGLETON = new NullChangeChandler();
	
	@Override
	public boolean isEqual(final ChangeHandler other)
	{
		return other == this;
	}
	
	@Override
	public void removeFromIndex(final long entityId)
	{
		// nothing to do
	}
	
	@Override
	public void changeInIndex(final long entityId, final ChangeHandler prevEntityHandler)
	{
		// even the null handler must make the prev handler remove the entityId
		prevEntityHandler.removeFromIndex(entityId);
	}
	
}
