package org.eclipse.store.gigamap.exceptions;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.types.BitmapIndex;


/**
 * A {@link BitmapIndexException} that additionally signals, via {@link StaleIndexException}, that the
 * bitmap index is stale relative to the current entity state (see {@link StaleIndexException} for the
 * causes and the implications for mutating operations).
 */
public class BitmapIndexStaleException extends BitmapIndexException implements StaleIndexException
{
	/**
	 * Constructs a new BitmapIndexStaleException with the specified detail message and associated
	 * BitmapIndex.
	 *
	 * @param message the detail message explaining the cause of the exception
	 * @param index the BitmapIndex detected to be stale
	 */
	public BitmapIndexStaleException(final String message, final BitmapIndex<?, ?> index)
	{
		super(message, index);
	}
}
