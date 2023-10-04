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


public interface StorageWriteController extends PersistenceWriteController
{
	public default void validateIsFileCleanupEnabled()
	{
		if(this.isFileCleanupEnabled())
		{
			return;
		}

		throw new StorageExceptionFileCleanupDisabled("File Cleanup is not enabled.");
	}
	
	public boolean isFileCleanupEnabled();
	
	
	
	public default void validateIsBackupEnabled()
	{
		if(this.isBackupEnabled())
		{
			return;
		}

		throw new StorageExceptionBackupDisabled("Backup is not enabled.");
	}
	
	public boolean isBackupEnabled();
	
	
	public default void validateIsDeletionDirectoryEnabled()
	{
		if(this.isDeletionDirectoryEnabled())
		{
			return;
		}

		throw new StorageExceptionDeletionDirectoryDisabled("Deletion directory is not enabled.");
	}
	
	public boolean isDeletionDirectoryEnabled();
	
	public default void validateIsFileDeletionEnabled()
	{
		if(this.isFileDeletionEnabled())
		{
			return;
		}

		throw new StorageExceptionFileDeletionDisabled("File deletion is not enabled.");
	}
	
	public boolean isFileDeletionEnabled();
	
	
	
	public static StorageWriteController Wrap(
		final WriteController writeController
	)
	{
		return new StorageWriteController.Wrapper(
			notNull(writeController)
		);
	}
	
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
