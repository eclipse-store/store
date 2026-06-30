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

import static org.eclipse.serializer.util.X.mayNull;
import static org.eclipse.serializer.util.X.notNull;

import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.typing.Immutable;

/**
 * Aggregated configuration for an embedded-storage instance, bundling the strategy parts that
 * collectively define how a {@link StorageManager} runs.
 * <p>
 * A {@link StorageConfiguration} fixes the channel count, file layout, housekeeping budget,
 * data-file rollover policy, entity-cache eviction policy and optional backup setup that the
 * foundation passes to the running storage. All parts are immutable per configuration; rebuilding
 * the configuration via the {@link Builder} is the supported way to vary individual strategies.
 * <p>
 * Default instances created via {@link #New()} use the framework defaults returned by the static
 * factories on {@link Storage} and a {@code null} {@link StorageBackupSetup} (no backup). Use
 * {@link #Builder()} to override individual parts.
 *
 * @see Storage
 * @see Builder
 * @see StorageManager
 */
public interface StorageConfiguration
{
	/**
	 * Returns the {@link StorageChannelCountProvider} that supplies the number of storage channels
	 * (and therefore the number of storage worker threads) used by the configured storage.
	 *
	 * @return the configured {@link StorageChannelCountProvider}.
	 */
	public StorageChannelCountProvider channelCountProvider();

	/**
	 * Returns the {@link StorageHousekeepingController} that defines the housekeeping interval and
	 * per-cycle time budgets (garbage collection, file cleanup, entity cache eviction) used by every
	 * channel.
	 *
	 * @return the configured {@link StorageHousekeepingController}.
	 */
	public StorageHousekeepingController housekeepingController();

	/**
	 * Returns the {@link StorageEntityCacheEvaluator} that decides when a live entity may have its
	 * cached binary representation evicted from memory.
	 *
	 * @return the configured {@link StorageEntityCacheEvaluator}.
	 */
	public StorageEntityCacheEvaluator entityCacheEvaluator();

	/* (10.12.2014 TM)TODO: consolidate StorageConfiguration#fileProvider with FileWriter and FileReader
	 * either move both here as well or move fileProvider out of here.
	 */
	/**
	 * Returns the {@link StorageLiveFileProvider} that resolves the on-disk locations of the live
	 * storage files (data files, transaction files, lock file).
	 *
	 * @return the configured {@link StorageLiveFileProvider}.
	 */
	public StorageLiveFileProvider fileProvider();

	/**
	 * Returns the {@link StorageDataFileEvaluator} that decides when a data file is full enough to be
	 * rolled over and when an existing data file qualifies for cleanup or dissolution.
	 *
	 * @return the configured {@link StorageDataFileEvaluator}.
	 */
	public StorageDataFileEvaluator dataFileEvaluator();

	/**
	 * Returns the {@link StorageBackupSetup} that controls continuous backup, or {@code null} if
	 * backup is disabled for this configuration.
	 *
	 * @return the configured {@link StorageBackupSetup}, or {@code null} if no backup is configured.
	 */
	public StorageBackupSetup backupSetup();

	/**
	 * Returns the {@link StorageChunkChecksumProvider} governing per-chunk checksum meta records
	 * (the hash algorithm plus whether {@code FileHeaderV1} / {@code ChunkChecksumV1} entries are
	 * written and verified).
	 * <p>
	 * Defined as a default method returning {@link StorageChunkChecksumProvider#New()} (the no-checksum
	 * default, i.e. the feature off) so that pre-existing {@link StorageConfiguration}
	 * implementations remain source- and binary-compatible.
	 *
	 * @return the configured {@link StorageChunkChecksumProvider}; never {@code null}.
	 */
	public default StorageChunkChecksumProvider chunkChecksumProvider()
	{
		return StorageChunkChecksumProvider.New();
	}


	/**
	 * Pseudo-constructor method to create a new {@link StorageConfiguration} instance
	 * using {@code null} as the {@link StorageBackupSetup} part and default instances for everything else.
	 * <p>
	 * For explanations and customizing values, see {@link StorageConfiguration.Builder}.
	 * 
	 * @return a new {@link StorageConfiguration} instance.
	 * 
	 * @see StorageConfiguration#New(StorageLiveFileProvider)
	 * @see StorageConfiguration.Builder
	 */
	public static StorageConfiguration New()
	{
		return StorageConfiguration.Builder()
			.createConfiguration()
		;
	}
	
	/**
	 * Pseudo-constructor method to create a new {@link StorageConfiguration} instance
	 * using the passed {@link StorageLiveFileProvider}, {@code null} as the {@link StorageBackupSetup} part
	 * and default instances for everything else.
	 * <p>
	 * For explanations and customizing values, see {@link StorageConfiguration.Builder}.
	 * 
	 * @param fileProvider the {@link StorageLiveFileProvider} to provide directory and file names.
	 * 
	 * @return a new {@link StorageConfiguration} instance.
	 * 
	 * @see StorageConfiguration#New()
	 * @see StorageConfiguration.Builder
	 */
	public static StorageConfiguration New(
		final StorageLiveFileProvider fileProvider
	)
	{
		return StorageConfiguration.Builder()
			.setStorageFileProvider(fileProvider)
			.createConfiguration()
		;
	}
	
	/**
	 * Pseudo-constructor method to create a new {@link StorageConfiguration} instance from the
	 * passed strategy parts.
	 * <p>
	 * All strategy arguments except {@code backupSetup} are required and must be non-{@code null};
	 * passing {@code null} for {@code backupSetup} disables backup for this configuration.
	 *
	 * @param channelCountProvider   the {@link StorageChannelCountProvider} to use; must be non-{@code null}.
	 * @param housekeepingController the {@link StorageHousekeepingController} to use; must be non-{@code null}.
	 * @param fileProvider           the {@link StorageLiveFileProvider} to use; must be non-{@code null}.
	 * @param dataFileEvaluator      the {@link StorageDataFileEvaluator} to use; must be non-{@code null}.
	 * @param entityCacheEvaluator   the {@link StorageEntityCacheEvaluator} to use; must be non-{@code null}.
	 * @param backupSetup            the {@link StorageBackupSetup} to use, or {@code null} to disable backup.
	 *
	 * @return a new {@link StorageConfiguration} instance with the passed parts.
	 */
	public static StorageConfiguration New(
		final StorageChannelCountProvider   channelCountProvider  ,
		final StorageHousekeepingController housekeepingController,
		final StorageLiveFileProvider           fileProvider          ,
		final StorageDataFileEvaluator      dataFileEvaluator     ,
		final StorageEntityCacheEvaluator   entityCacheEvaluator  ,
		final StorageBackupSetup            backupSetup
	)
	{
		return New(
			channelCountProvider  ,
			housekeepingController,
			fileProvider          ,
			dataFileEvaluator     ,
			entityCacheEvaluator  ,
			backupSetup           ,
			StorageChunkChecksumProvider.New()
		);
	}

	/**
	 * Pseudo-constructor method to create a new {@link StorageConfiguration} instance from the
	 * passed strategy parts, including an explicit {@link StorageChunkChecksumProvider}.
	 *
	 * @param channelCountProvider   the {@link StorageChannelCountProvider} to use; must be non-{@code null}.
	 * @param housekeepingController the {@link StorageHousekeepingController} to use; must be non-{@code null}.
	 * @param fileProvider           the {@link StorageLiveFileProvider} to use; must be non-{@code null}.
	 * @param dataFileEvaluator      the {@link StorageDataFileEvaluator} to use; must be non-{@code null}.
	 * @param entityCacheEvaluator   the {@link StorageEntityCacheEvaluator} to use; must be non-{@code null}.
	 * @param backupSetup            the {@link StorageBackupSetup} to use, or {@code null} to disable backup.
	 * @param chunkChecksumProvider  the {@link StorageChunkChecksumProvider} to use; must be non-{@code null}.
	 *
	 * @return a new {@link StorageConfiguration} instance with the passed parts.
	 */
	public static StorageConfiguration New(
		final StorageChannelCountProvider   channelCountProvider  ,
		final StorageHousekeepingController housekeepingController,
		final StorageLiveFileProvider           fileProvider          ,
		final StorageDataFileEvaluator      dataFileEvaluator     ,
		final StorageEntityCacheEvaluator   entityCacheEvaluator  ,
		final StorageBackupSetup            backupSetup           ,
		final StorageChunkChecksumProvider  chunkChecksumProvider
	)
	{
		return new StorageConfiguration.Default(
			notNull(channelCountProvider)  ,
			notNull(housekeepingController),
			notNull(fileProvider)          ,
			notNull(dataFileEvaluator)     ,
			notNull(entityCacheEvaluator)  ,
			mayNull(backupSetup)           ,
			notNull(chunkChecksumProvider)
		);
	}

	/**
	 * Default immutable implementation of {@link StorageConfiguration}: a value-style aggregator that
	 * simply holds the passed strategy parts and exposes them via the interface getters.
	 */
	public class Default implements StorageConfiguration, Immutable
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final StorageChannelCountProvider   channelCountProvider  ;
		private final StorageHousekeepingController housekeepingController;
		private final StorageLiveFileProvider           fileProvider          ;
		private final StorageDataFileEvaluator      dataFileEvaluator     ;
		private final StorageEntityCacheEvaluator   entityCacheEvaluator  ;
		private final StorageBackupSetup            backupSetup           ;
		private final StorageChunkChecksumProvider  chunkChecksumProvider ;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final StorageChannelCountProvider   channelCountProvider  ,
			final StorageHousekeepingController housekeepingController,
			final StorageLiveFileProvider           fileProvider          ,
			final StorageDataFileEvaluator      dataFileEvaluator     ,
			final StorageEntityCacheEvaluator   entityCacheEvaluator  ,
			final StorageBackupSetup            backupSetup           ,
			final StorageChunkChecksumProvider  chunkChecksumProvider
		)
		{
			super();
			this.channelCountProvider   = channelCountProvider  ;
			this.housekeepingController = housekeepingController;
			this.entityCacheEvaluator   = entityCacheEvaluator  ;
			this.fileProvider           = fileProvider          ;
			this.dataFileEvaluator      = dataFileEvaluator     ;
			this.backupSetup            = backupSetup           ;
			this.chunkChecksumProvider  = chunkChecksumProvider ;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public StorageChannelCountProvider channelCountProvider()
		{
			return this.channelCountProvider;
		}

		@Override
		public StorageHousekeepingController housekeepingController()
		{
			return this.housekeepingController;
		}

		@Override
		public StorageEntityCacheEvaluator entityCacheEvaluator()
		{
			return this.entityCacheEvaluator;
		}

		@Override
		public StorageLiveFileProvider fileProvider()
		{
			return this.fileProvider;
		}

		@Override
		public StorageDataFileEvaluator dataFileEvaluator()
		{
			return this.dataFileEvaluator;
		}
		
		@Override
		public StorageBackupSetup backupSetup()
		{
			return this.backupSetup;
		}

		@Override
		public StorageChunkChecksumProvider chunkChecksumProvider()
		{
			return this.chunkChecksumProvider;
		}

		@Override
		public String toString()
		{
			return VarString.New()
				.add(this.getClass().getName()  ).add(':').lf()
				.add(this.channelCountProvider  ).lf()
				.add(this.fileProvider          ).lf()
				.add(this.housekeepingController).lf()
				.add(this.entityCacheEvaluator  ).lf()
				.add(this.dataFileEvaluator     ).lf()
				.add(this.backupSetup == null ? StorageBackupSetup.class.getName() + ": null": this.backupSetup).lf()
				.add(this.chunkChecksumProvider ).lf()
				.toString()
			;
		}

	}
	
	
	/**
	 * Pseudo-constructor method to create a new {@link StorageConfiguration.Builder} instance.
	 * <p>
	 * For explanations and customizing values, see {@link StorageConfiguration.Builder}.
	 * 
	 * @return a new {@link StorageConfiguration.Builder} instance.
	 * 
	 * @see StorageConfiguration.Builder
	 * @see StorageConfiguration
	 */
	public static StorageConfiguration.Builder<?> Builder()
	{
		return new StorageConfiguration.Builder.Default<>();
	}
	
	/**
	 * Mutable, fluent builder for {@link StorageConfiguration} instances.
	 * <p>
	 * Each strategy part starts out at the framework default returned by the corresponding factory on
	 * {@link Storage}; setters replace the current value and return {@code this} to allow method
	 * chaining. Passing {@code null} to a non-backup setter resets the corresponding part to its
	 * framework default; passing {@code null} to {@link #setBackupSetup(StorageBackupSetup)} disables
	 * backup for the resulting configuration.
	 *
	 * @param <B> the self-type of the concrete builder, returned by every setter to enable
	 *            type-safe fluent chaining in subclasses.
	 */
	public interface Builder<B extends Builder<?>>
	{
		/**
		 * Returns the currently configured {@link StorageChannelCountProvider}.
		 *
		 * @return the current {@link StorageChannelCountProvider}.
		 */
		public StorageChannelCountProvider channelCountProvider();

		/**
		 * Sets the {@link StorageChannelCountProvider} to be used by the resulting configuration.
		 * Passing {@code null} resets the value to the framework default.
		 *
		 * @param channelCountProvider the new {@link StorageChannelCountProvider}, or {@code null} to reset.
		 *
		 * @return this builder, for fluent chaining.
		 */
		public B setChannelCountProvider(StorageChannelCountProvider channelCountProvider);

		/**
		 * Returns the currently configured {@link StorageHousekeepingController}.
		 *
		 * @return the current {@link StorageHousekeepingController}.
		 */
		public StorageHousekeepingController housekeepingController();

		/**
		 * Sets the {@link StorageHousekeepingController} to be used by the resulting configuration.
		 * Passing {@code null} resets the value to the framework default.
		 *
		 * @param housekeepingController the new {@link StorageHousekeepingController}, or {@code null} to reset.
		 *
		 * @return this builder, for fluent chaining.
		 */
		public B setHousekeepingController(StorageHousekeepingController housekeepingController);

		/**
		 * Returns the currently configured {@link StorageLiveFileProvider}.
		 *
		 * @return the current {@link StorageLiveFileProvider}.
		 */
		public StorageLiveFileProvider storagefileProvider();

		/**
		 * Sets the {@link StorageLiveFileProvider} to be used by the resulting configuration. Passing
		 * {@code null} resets the value to the framework default.
		 *
		 * @param liveFileProvider the new {@link StorageLiveFileProvider}, or {@code null} to reset.
		 *
		 * @return this builder, for fluent chaining.
		 */
		public B setStorageFileProvider(StorageLiveFileProvider liveFileProvider);

		/**
		 * Returns the currently configured {@link StorageBackupSetup}, or {@code null} if backup is
		 * currently disabled.
		 *
		 * @return the current {@link StorageBackupSetup}, or {@code null} if backup is disabled.
		 */
		public StorageBackupSetup backupSetup();

		/**
		 * Sets the {@link StorageBackupSetup} to be used by the resulting configuration. Passing
		 * {@code null} disables backup for the resulting configuration; in contrast to the other
		 * setters, no default is filled in.
		 *
		 * @param backupSetup the new {@link StorageBackupSetup}, or {@code null} to disable backup.
		 *
		 * @return this builder, for fluent chaining.
		 */
		public B setBackupSetup(StorageBackupSetup backupSetup);

		/**
		 * Returns the currently configured {@link StorageDataFileEvaluator}.
		 *
		 * @return the current {@link StorageDataFileEvaluator}.
		 */
		public StorageDataFileEvaluator dataFileEvaluator();

		/**
		 * Sets the {@link StorageDataFileEvaluator} to be used by the resulting configuration. Passing
		 * {@code null} resets the value to the framework default.
		 *
		 * @param dataFileEvaluator the new {@link StorageDataFileEvaluator}, or {@code null} to reset.
		 *
		 * @return this builder, for fluent chaining.
		 */
		public B setDataFileEvaluator(StorageDataFileEvaluator dataFileEvaluator);

		/**
		 * Returns the currently configured {@link StorageEntityCacheEvaluator}.
		 *
		 * @return the current {@link StorageEntityCacheEvaluator}.
		 */
		public StorageEntityCacheEvaluator entityCacheEvaluator();

		/**
		 * Sets the {@link StorageEntityCacheEvaluator} to be used by the resulting configuration.
		 * Passing {@code null} resets the value to the framework default.
		 *
		 * @param entityCacheEvaluator the new {@link StorageEntityCacheEvaluator}, or {@code null} to reset.
		 *
		 * @return this builder, for fluent chaining.
		 */
		public B setEntityCacheEvaluator(StorageEntityCacheEvaluator entityCacheEvaluator);

		/**
		 * Returns the currently configured {@link StorageChunkChecksumProvider}.
		 *
		 * @return the current {@link StorageChunkChecksumProvider}.
		 */
		public StorageChunkChecksumProvider chunkChecksumProvider();

		/**
		 * Sets the {@link StorageChunkChecksumProvider} to be used by the resulting configuration.
		 * Passing {@code null} resets the value to the framework default
		 * ({@link StorageChunkChecksumProvider#New()}: no checksum, i.e. the feature off).
		 *
		 * @param chunkChecksumProvider the new {@link StorageChunkChecksumProvider}, or {@code null} to reset.
		 *
		 * @return this builder, for fluent chaining.
		 */
		public B setChunkChecksumProvider(StorageChunkChecksumProvider chunkChecksumProvider);

		/**
		 * Builds a new {@link StorageConfiguration} from the strategy parts currently held by this
		 * builder.
		 * <p>
		 * The builder remains usable after this call — subsequent setter calls do not affect already
		 * created configurations.
		 *
		 * @return a new {@link StorageConfiguration} instance reflecting the current builder state.
		 */
		public StorageConfiguration createConfiguration();



		/**
		 * Default implementation of {@link Builder} using the framework default strategy parts from
		 * {@link Storage} as initial values. Subclasses can override the {@code initialize~} hooks to
		 * change individual defaults.
		 *
		 * @param <B> the self-type of the concrete builder subclass.
		 */
		public class Default<B extends Builder.Default<?>> implements StorageConfiguration.Builder<B>
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////

			private StorageChannelCountProvider   channelCountProvider   = this.initializeChannelCountProvider();
			private StorageHousekeepingController housekeepingController = this.initializeHousekeepingController();
			private StorageLiveFileProvider       storageFileProvider    = this.initializeLiveFileProvider();
			private StorageDataFileEvaluator      dataFileEvaluator      = this.initializeDataFileEvaluator();
			private StorageEntityCacheEvaluator   entityCacheEvaluator   = this.initializeEntityCacheEvaluator();
			private StorageChunkChecksumProvider  chunkChecksumProvider  = this.initializeChunkChecksumProvider();
			private StorageBackupSetup            backupSetup           ; // optional
			
			
			
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
			
			protected StorageChannelCountProvider initializeChannelCountProvider()
			{
				return Storage.ChannelCountProvider();
			}
			
			protected StorageHousekeepingController initializeHousekeepingController()
			{
				return Storage.HousekeepingController();
			}
			
			protected StorageLiveFileProvider initializeLiveFileProvider()
			{
				return Storage.FileProvider();
			}
			
			protected StorageDataFileEvaluator initializeDataFileEvaluator()
			{
				return Storage.DataFileEvaluator();
			}
			
			protected StorageEntityCacheEvaluator initializeEntityCacheEvaluator()
			{
				return Storage.EntityCacheEvaluator();
			}

			protected StorageChunkChecksumProvider initializeChunkChecksumProvider()
			{
				return Storage.ChunkChecksumProvider();
			}
			
			@SuppressWarnings("unchecked")
			protected final B $()
			{
				return (B)this;
			}
			
			@Override
			public StorageChannelCountProvider channelCountProvider()
			{
				return this.channelCountProvider;
			}
			
			@Override
			public B setChannelCountProvider(final StorageChannelCountProvider channelCountProvider)
			{
				this.channelCountProvider = channelCountProvider == null
					? this.initializeChannelCountProvider()
					: channelCountProvider
				;
				return this.$();
			}
			
			@Override
			public StorageHousekeepingController housekeepingController()
			{
				return this.housekeepingController;
			}
			
			@Override
			public B setHousekeepingController(final StorageHousekeepingController housekeepingController)
			{
				this.housekeepingController = housekeepingController == null
					? this.initializeHousekeepingController()
					: housekeepingController
				;
				return this.$();
			}
			
			@Override
			public StorageLiveFileProvider storagefileProvider()
			{
				return this.storageFileProvider;
			}
			
			@Override
			public B setStorageFileProvider(final StorageLiveFileProvider liveFileProvider)
			{
				this.storageFileProvider = liveFileProvider == null
					? this.initializeLiveFileProvider()
					: liveFileProvider
				;
				
				return this.$();
			}
			
			@Override
			public StorageBackupSetup backupSetup()
			{
				return this.backupSetup;
			}
						
			@Override
			public B setBackupSetup(final StorageBackupSetup backupSetup)
			{
				// may be null
				this.backupSetup = backupSetup;
				return this.$();
			}
			
			@Override
			public StorageDataFileEvaluator dataFileEvaluator()
			{
				return this.dataFileEvaluator;
			}
			
			@Override
			public B setDataFileEvaluator(final StorageDataFileEvaluator dataFileEvaluator)
			{
				this.dataFileEvaluator = dataFileEvaluator == null
					? this.initializeDataFileEvaluator()
					: dataFileEvaluator
				;
				return this.$();
			}
			
			@Override
			public StorageEntityCacheEvaluator entityCacheEvaluator()
			{
				return this.entityCacheEvaluator;
			}
			
			@Override
			public B setEntityCacheEvaluator(final StorageEntityCacheEvaluator entityCacheEvaluator)
			{
				this.entityCacheEvaluator = entityCacheEvaluator == null
					? this.initializeEntityCacheEvaluator()
					: entityCacheEvaluator
				;
				return this.$();
			}

			@Override
			public StorageChunkChecksumProvider chunkChecksumProvider()
			{
				return this.chunkChecksumProvider;
			}

			@Override
			public B setChunkChecksumProvider(final StorageChunkChecksumProvider chunkChecksumProvider)
			{
				this.chunkChecksumProvider = chunkChecksumProvider == null
					? this.initializeChunkChecksumProvider()
					: chunkChecksumProvider
				;
				return this.$();
			}

			@Override
			public StorageConfiguration createConfiguration()
			{
				return StorageConfiguration.New(
					this.channelCountProvider  ,
					this.housekeepingController,
					this.storageFileProvider   ,
					this.dataFileEvaluator     ,
					this.entityCacheEvaluator  ,
					this.backupSetup           ,
					this.chunkChecksumProvider
				);
			}
			
		}
		
	}

}
