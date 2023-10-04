package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
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

public interface StorageObjectIdRangeEvaluator
{
	public void evaluateObjectIdRange(long lowestObjectId, long highestObjectId);


	public final class Default implements StorageObjectIdRangeEvaluator
	{
		@Override
		public void evaluateObjectIdRange(final long lowestObjectId, final long highestObjectId)
		{
			// no-op default implementation.
		}

	}
}
