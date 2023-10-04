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

public interface StorageKillable
{
	/**
	 * Stops all threads, releases all resources (e.g. close files) without considering any internal state
	 * or waiting for any action to be completed.<p>
	 * Useful only in simple error cases, for example
	 * 
	 * @param cause the reason for the kill
	 */
	public void killStorage(Throwable cause);
}
