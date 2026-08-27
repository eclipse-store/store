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

/**
 * Common identity contract shared by every part of a logical database — most prominently
 * {@link Database} itself and its associated {@link StorageManager}.
 * <p>
 * Each running database is uniquely identified by a non-empty {@link #databaseName() database name}.
 * The name is set once when the {@link Database} entry is created in {@link Databases} and never changes
 * afterwards, which allows this interface to act as a stable correlation key between the various parts
 * (database handle, storage manager, connections) that belong to the same database instance.
 *
 * @see Database
 * @see Databases
 * @see StorageManager
 */
public interface DatabasePart
{
	/**
	 * Returns the identifying name of the {@link Database} this part belongs to.
	 *
	 * @return the identifying name of the {@link Database}.
	 */
	public String databaseName();
}
