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

public interface StorageChannelResetablePart extends StorageHashChannelPart
{
	/**
	 * Closes all resources (files, locks, etc.).
	 * Clears all variable length items (cache, registry, etc.).
	 * Resets internal state to initial values.
	 * For itself and all its parts (entity cache, file manager, etc.).
	 * Basically a "back to just being born" action.
	 */
	public void reset();
}
