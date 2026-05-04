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

import static org.eclipse.serializer.util.X.notNull;

import org.eclipse.serializer.afs.types.WriteController;
import org.eclipse.serializer.persistence.types.PersistenceWriteController;
import org.eclipse.store.storage.exceptions.StorageExceptionBackupDisabled;
import org.eclipse.store.storage.exceptions.StorageExceptionDeletionDirectoryDisabled;
import org.eclipse.store.storage.exceptions.StorageExceptionFileCleanupDisabled;
import org.eclipse.store.storage.exceptions.StorageExceptionFileDeletionDisabled;


/**
 * Per-storage policy hook that gates every operation which mutates the on-disk state — writing
 * entities, cleaning up data files, deleting files, and continuous backup.
 * <p>
 * The interface combines the inherited {@link PersistenceWriteController} writability gate with
 * additional fine-grained switches that are specific to storage-level housekeeping. Each query
 * method has a paired {@code validate~} default method that throws a typed
 * {@link org.eclipse.store.storage.exceptions.StorageException} if the corresponding capability
 * is not enabled, allowing the storage to fail fast with a descriptive error instead of silently
 * skipping the requested action.
 * <p>
 * The default wiring uses {@link #Wrap(WriteController) a wrapper} that ties every storage capability
 * to the underlying {@link WriteController}'s writability flag, so that a single read-only switch
 * disables every form of mutation. Custom implementations can decouple the switches — for example
 * to keep stores enabled but suppress file cleanup during a maintenance window.
 *
 * @see WriteController
 * @see PersistenceWriteController
 */
public interface StorageWriteController extends PersistenceWriteController
{
	/**
	 * Throws a {@link StorageExceptionFileCleanupDisabled} if {@link #isFileCleanupEnabled()} is
	 * {@code false}; returns silently otherwise.
	 *
	 * @throws StorageExceptionFileCleanupDisabled if file cleanup is disabled.
	 */
	public default void validateIsFileCleanupEnabled()
	{
		if(this.isFileCleanupEnabled())
		{
			return;
		}

		throw new StorageExceptionFileCleanupDisabled("File Cleanup is not enabled.");
	}

	/**
	 * Returns whether the storage may currently perform file-cleanup work (consolidating and
	 * dissolving data files).
	 *
	 * @return {@code true} if file cleanup is enabled.
	 */
	public boolean isFileCleanupEnabled();



	/**
	 * Throws a {@link StorageExceptionBackupDisabled} if {@link #isBackupEnabled()} is
	 * {@code false}; returns silently otherwise.
	 *
	 * @throws StorageExceptionBackupDisabled if backup is disabled.
	 */
	public default void validateIsBackupEnabled()
	{
		if(this.isBackupEnabled())
		{
			return;
		}

		throw new StorageExceptionBackupDisabled("Backup is not enabled.");
	}

	/**
	 * Returns whether continuous backup may currently mirror writes to the configured backup target.
	 *
	 * @return {@code true} if backup is enabled.
	 */
	public boolean isBackupEnabled();


	/**
	 * Throws a {@link StorageExceptionDeletionDirectoryDisabled} if
	 * {@link #isDeletionDirectoryEnabled()} is {@code false}; returns silently otherwise.
	 *
	 * @throws StorageExceptionDeletionDirectoryDisabled if the deletion directory is disabled.
	 */
	public default void validateIsDeletionDirectoryEnabled()
	{
		if(this.isDeletionDirectoryEnabled())
		{
			return;
		}

		throw new StorageExceptionDeletionDirectoryDisabled("Deletion directory is not enabled.");
	}

	/**
	 * Returns whether the storage may currently move dissolved data files to the configured deletion
	 * directory (rather than deleting them outright).
	 *
	 * @return {@code true} if the deletion directory is enabled.
	 */
	public boolean isDeletionDirectoryEnabled();

	/**
	 * Throws a {@link StorageExceptionFileDeletionDisabled} if {@link #isFileDeletionEnabled()} is
	 * {@code false}; returns silently otherwise.
	 *
	 * @throws StorageExceptionFileDeletionDisabled if file deletion is disabled.
	 */
	public default void validateIsFileDeletionEnabled()
	{
		if(this.isFileDeletionEnabled())
		{
			return;
		}

		throw new StorageExceptionFileDeletionDisabled("File deletion is not enabled.");
	}

	/**
	 * Returns whether the storage may currently delete obsolete data files from disk.
	 *
	 * @return {@code true} if file deletion is enabled.
	 */
	public boolean isFileDeletionEnabled();



	/**
	 * Pseudo-constructor method that creates a {@link StorageWriteController} which delegates every
	 * capability to the passed {@link WriteController}'s writability flag.
	 * <p>
	 * Storing, file cleanup, backup, deletion directory and file deletion are all enabled exactly
	 * when the wrapped {@link WriteController#isWritable()} returns {@code true}. This is the
	 * default wiring used by the embedded-storage foundation.
	 *
	 * @param writeController the underlying {@link WriteController} to delegate to.
	 *
	 * @return a {@link StorageWriteController} backed by the passed controller.
	 */
	public static StorageWriteController Wrap(
		final WriteController writeController
	)
	{
		return new StorageWriteController.Wrapper(
			notNull(writeController)
		);
	}

	/**
	 * Default {@link StorageWriteController} implementation that ties every storage-level capability
	 * to the writability flag of an underlying {@link WriteController}: when the wrapped controller
	 * is read-only, every storage capability is disabled.
	 */
	public final class Wrapper implements StorageWriteController
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final WriteController writeController;

		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Wrapper(final WriteController writeController)
		{
			super();
			this.writeController = writeController;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final void validateIsWritable()
		{
			this.writeController.validateIsWritable();
		}
		
		@Override
		public final boolean isWritable()
		{
			return this.writeController.isWritable();
		}
		
		@Override
		public final void validateIsStoringEnabled()
		{
			this.validateIsWritable();
		}
		
		@Override
		public final boolean isStoringEnabled()
		{
			return this.isWritable();
		}
		
		@Override
		public final boolean isFileCleanupEnabled()
		{
			return this.isWritable();
		}
		
		@Override
		public final boolean isBackupEnabled()
		{
			return this.isWritable();
		}
		
		@Override
		public final boolean isDeletionDirectoryEnabled()
		{
			return this.isWritable();
		}
		
		@Override
		public final boolean isFileDeletionEnabled()
		{
			return this.isWritable();
		}
				
	}
		
}
