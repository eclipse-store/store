package org.eclipse.store.storage.types;

import java.util.concurrent.ThreadFactory;

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
public interface StorageLockFileManagerThreadProvider extends StorageThreadProviding, ThreadFactory
{
	/**
	 * Provide a ThreadFactory for the StorageLockFileManager that uses the configured StorageThreadNameProvider
	 * to name threads.
	 */
	@Override
	public default Thread newThread(final Runnable runnable)
	{
		return this.provideLockFileManagerThread(runnable, StorageThreadNameProvider.NoOp());
	}
	


	@Deprecated
	public default Thread provideLockFileManagerThread(final Runnable runnable)
	{
		return this.provideLockFileManagerThread(runnable, StorageThreadNameProvider.NoOp());
	}
	

	@Deprecated
	public Thread provideLockFileManagerThread(
		Runnable                  runnable          ,
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
			final Runnable                  runnable          ,
			final StorageThreadNameProvider threadNameProvider
		)
		{
			final String threadName = StorageLockFileManager.class.getSimpleName();
			
			return new Thread(
				runnable,
				threadNameProvider.provideThreadName(this, threadName)
			);
		}

	}

}
