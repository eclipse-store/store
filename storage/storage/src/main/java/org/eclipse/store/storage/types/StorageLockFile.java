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

import org.eclipse.serializer.afs.types.AFile;

import static org.eclipse.serializer.util.X.notNull;

/**
 * The lock file used by an embedded storage to assert exclusive single-writer access to its data
 * location.
 * <p>
 * On startup the storage opens this file, writes its identity (PID, host, and a heartbeat
 * timestamp) and continuously refreshes the heartbeat while running. Other JVMs that try to start
 * a second writable storage on the same data location detect the active lock file and fail with a
 * descriptive error. The file is released when the storage is shut down or — in the read-only
 * mode — never acquired exclusively at all.
 *
 * @see StorageLiveFileProvider#provideLockFile()
 * @see StorageLockFileManager
 */
public interface StorageLockFile extends StorageClosableFile
{
	/**
	 * Pseudo-constructor method to wrap the passed {@link AFile} as a {@link StorageLockFile}.
	 *
	 * @param file the underlying {@link AFile} to use as the lock file; must be non-{@code null}.
	 *
	 * @return a new {@link StorageLockFile} backed by the passed {@link AFile}.
	 */
	public static StorageLockFile New(final AFile file)
	{
		return new StorageLockFile.Default(
			notNull(file)
		);
	}

	/**
	 * Default {@link StorageLockFile} implementation: a thin specialization of
	 * {@link StorageFile.Abstract} that adds no additional behavior beyond the type-marker.
	 */
	public final class Default extends StorageFile.Abstract implements StorageLockFile
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		protected Default(final AFile file)
		{
			super(file);
		}

	}

}
