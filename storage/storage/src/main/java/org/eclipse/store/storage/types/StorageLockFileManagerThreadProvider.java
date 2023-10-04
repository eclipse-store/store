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

@FunctionalInterface
public interface StorageLockFileManagerThreadProvider extends StorageThreadProviding
{
	/**
	 * Provides a newly created, yet un-started {@link Thread} instance wrapping the passed
	 * {@link StorageLockFileManager} instance.
	 * The thread will be used as an exclusive, permanent lock file validator and updater worker thread
	 * until the storage is shut down.
	 * Interfering with the thread from outside the storage compound has undefined and potentially
	 * unpredictable and erroneous behavior.
	 * @param lockFileManager the lock file manager to wrap
	 * @return a {@link Thread} instance to be used as a storage lock file managing worker thread.
	 */
	public default Thread provideLockFileManagerThread(final StorageLockFileManager lockFileManager)
	{
		return this.provideLockFileManagerThread(lockFileManager, StorageThreadNameProvider.NoOp());
	}
	
	public Thread provideLockFileManagerThread(
		StorageLockFileManager    lockFileManager   ,
		StorageThreadNameProvider threadNameProvider
	);
	

	
	public static StorageLockFileManagerThreadProvider New()
	{
		return new StorageLockFileManagerThreadProvider.Default();
	}

	public final class Default implements StorageLockFileManagerThreadProvider
	{
		Default()
		{
			super();
		}
		
		@Override
		public Thread provideLockFileManagerThread(
			final StorageLockFileManager    lockFileManager   ,
			final StorageThreadNameProvider threadNameProvider
		)
		{
			final String threadName = StorageLockFileManager.class.getSimpleName();
			
			return new Thread(
				lockFileManager,
				threadNameProvider.provideThreadName(this, threadName)
			);
		}

	}

}
