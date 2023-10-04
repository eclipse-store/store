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
public interface StorageBackupThreadProvider extends StorageThreadProviding
{
	/**
	 * Provides a newly created, yet un-started {@link Thread} instance wrapping the passed
	 * {@link StorageBackupHandler} instance.
	 * The thread will be used as an exclusive, permanent backup worker thread until the storage
	 * is shut down.
	 * Interfering with the thread from outside the storage compound has undefined and potentially
	 * unpredictable and erronous behavior.
	 *
	 * @param backupHandler the handler to wrap
	 * @return a {@link Thread} instance to be used as a storage backup worker thread.
	 */
	public default Thread provideBackupThread(final StorageBackupHandler backupHandler)
	{
		return this.provideBackupThread(backupHandler, StorageThreadNameProvider.NoOp());
	}
	
	public Thread provideBackupThread(
		StorageBackupHandler      backupHandler     ,
		StorageThreadNameProvider threadNameProvider
	);

	
	
	public static StorageBackupThreadProvider New()
	{
		return new StorageBackupThreadProvider.Default();
	}

	public final class Default implements StorageBackupThreadProvider
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default()
		{
			super();
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public Thread provideBackupThread(
			final StorageBackupHandler      backupHandler     ,
			final StorageThreadNameProvider threadNameProvider
		)
		{
			final String threadName = StorageBackupHandler.class.getSimpleName();
			
			return new Thread(
				backupHandler,
				threadNameProvider.provideThreadName(this, threadName)
			);
		}

	}

}
