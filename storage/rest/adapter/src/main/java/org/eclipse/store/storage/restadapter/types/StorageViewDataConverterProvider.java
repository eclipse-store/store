package org.eclipse.store.storage.restadapter.types;

/*-
 * #%L
 * EclipseStore Storage REST Adapter
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

public interface StorageViewDataConverterProvider
{
	/**
	 * Get the converter for the requested format.
	 *
	 * @param format the format to get the converter for
	 * @return the registered converter, or <code>null</code> if none was found
	 */
	public StorageViewDataConverter getConverter(String format);
}
