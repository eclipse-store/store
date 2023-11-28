
package org.eclipse.store.cache.types;

/*-
 * #%L
 * microstream-cache
 * %%
 * Copyright (C) 2019 - 2022 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import javax.cache.processor.EntryProcessorException;


public interface EntryProcessorResult<T> extends javax.cache.processor.EntryProcessorResult<T>
{
	public static <T> EntryProcessorResult<T> New(final T value)
	{
		return () -> value;
	}
	
	public static <T> EntryProcessorResult<T> New(final Exception e)
	{
		final EntryProcessorException epe = e instanceof EntryProcessorException
			? (EntryProcessorException)e
			: new EntryProcessorException(e);
		return () -> {
			throw epe;
		};
	}
}
