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

import java.nio.ByteBuffer;

import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.binary.types.BinaryEntityRawDataIterator;
import org.eclipse.store.storage.exceptions.StorageExceptionCommitSizeExceeded;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;


public interface StorageDataChunkValidator
{
	public void validateDataChunk(Binary data);

		
	public static StorageDataChunkValidator New(
		final BinaryEntityRawDataIterator entityDataIterator ,
		final StorageEntityDataValidator  entityDataValidator
	)
	{
		return new StorageDataChunkValidator.Default(
			notNull(entityDataIterator),
			notNull(entityDataValidator)
		);
	}
	
	public final class Default implements StorageDataChunkValidator
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final BinaryEntityRawDataIterator entityDataIterator ;
		private final StorageEntityDataValidator  entityDataValidator;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final BinaryEntityRawDataIterator entityDataIterator ,
			final StorageEntityDataValidator  entityDataValidator
		)
		{
			super();
			this.entityDataIterator  = entityDataIterator ;
			this.entityDataValidator = entityDataValidator;
		}

		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public void validateDataChunk(final Binary data)
		{
			data.iterateChannelChunks(this::iterateChannelChunk);
		}
		
		private void iterateChannelChunk(final Binary cc)
		{
			final BinaryEntityRawDataIterator iterator  = this.entityDataIterator ;
			final StorageEntityDataValidator  validator = this.entityDataValidator;
			
			for(final ByteBuffer bb : cc.buffers())
			{
				final long remainingLength = iterator.iterateEntityRawData(
						XMemory.getDirectByteBufferAddress(bb),
						XMemory.getDirectByteBufferAddress(bb) + bb.limit(),
						validator
				);
				if(remainingLength != 0)
				{
					throw new StorageExceptionConsistency(
						"Entity data chunk inconsistency: " + remainingLength + " remaining bytes of " + bb.limit()
					);
				}
			}
		}
		
	}
	
	public static StorageDataChunkValidator.Provider Provider(
		final BinaryEntityRawDataIterator.Provider entityDataIteratorProvider,
		final StorageEntityDataValidator.Creator   entityDataValidatorCreator
	)
	{
		return new StorageDataChunkValidator.Provider.Default(
			notNull(entityDataIteratorProvider),
			notNull(entityDataValidatorCreator)
		);
	}

	public interface Provider
	{
		public StorageDataChunkValidator provideDataChunkValidator(StorageTypeDictionary typeDictionary);
		
		
		public final class Default implements StorageDataChunkValidator.Provider
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////

			private final BinaryEntityRawDataIterator.Provider entityDataIteratorProvider;
			private final StorageEntityDataValidator.Creator   entityDataValidatorCreator;
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			Default(
				final BinaryEntityRawDataIterator.Provider entityDataIteratorProvider,
				final StorageEntityDataValidator.Creator   entityDataValidatorCreator
			)
			{
				super();
				this.entityDataIteratorProvider = entityDataIteratorProvider;
				this.entityDataValidatorCreator = entityDataValidatorCreator;
			}


			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////

			@Override
			public final StorageDataChunkValidator provideDataChunkValidator(
				final StorageTypeDictionary typeDictionary
			)
			{
				return StorageDataChunkValidator.New(
					this.entityDataIteratorProvider.provideEntityDataIterator(),
					this.entityDataValidatorCreator.createDataFileValidator(typeDictionary)
				);
			}
			
		}
		
		public final class Transient implements StorageDataChunkValidator.Provider
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			private final StorageDataChunkValidator validator;
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			Transient(final StorageDataChunkValidator validator)
			{
				super();
				this.validator = validator;
			}


			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////

			@Override
			public StorageDataChunkValidator provideDataChunkValidator(
				final StorageTypeDictionary typeDictionary
			)
			{
				return this.validator;
			}
		}
	}
	
	public static StorageDataChunkValidator.Provider Wrap(
		final StorageDataChunkValidator validator
	)
	{
		return new StorageDataChunkValidator.Provider.Transient(
			notNull(validator)
		);
	}
	
	public static StorageDataChunkValidator.Provider2 Wrap2(
		final StorageDataChunkValidator validator
	)
	{
		return Wrap2(
			Wrap(
				notNull(validator)
			)
		);
	}
	
	public static StorageDataChunkValidator.Provider2 Wrap2(
		final StorageDataChunkValidator.Provider provider
	)
	{
		return new StorageDataChunkValidator.Provider2.Transient(
			notNull(provider)
		);
	}
	
	public static StorageDataChunkValidator.Provider2 Provider2()
	{
		return new StorageDataChunkValidator.Provider2.Default();
	}
	
	/**
	 * "Provider2" ist not a lazy copy name of "Provider", it's a hereby introduced schema to indicate
	 * multi-layered provider logic which indicates that this is actually a "ProviderProvider".
	 * With multiple layers of interface-based architecture, multiple layers of providers are necessary.
	 *
	 */
	public interface Provider2
	{
		public StorageDataChunkValidator.Provider provideDataChunkValidatorProvider(StorageFoundation<?> foundation);
		
		
		
		public final class Default implements StorageDataChunkValidator.Provider2
		{
			@Override
			public StorageDataChunkValidator.Provider provideDataChunkValidatorProvider(final StorageFoundation<?> foundation)
			{
				return StorageDataChunkValidator.Provider(
					foundation.getEntityDataIteratorProvider(),
					foundation.getEntityDataValidatorCreator()
				);
			}
			
		}
		
		public final class Transient implements StorageDataChunkValidator.Provider2
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			private final StorageDataChunkValidator.Provider provider;
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			Transient(final StorageDataChunkValidator.Provider provider)
			{
				super();
				this.provider = provider;
			}


			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////

			@Override
			public StorageDataChunkValidator.Provider provideDataChunkValidatorProvider(
				final StorageFoundation<?> foundation
			)
			{
				return this.provider;
			}
		}
	}



	public final class NoOp implements StorageDataChunkValidator, Provider, Provider2
	{
		@Override
		public final StorageDataChunkValidator provideDataChunkValidator(
			final StorageTypeDictionary typeDictionary
		)
		{
			return this;
		}

		@Override
		public final void validateDataChunk(
			final Binary data
		)
		{
			// no-op
		}
		
		@Override
		public final Provider provideDataChunkValidatorProvider(
			final StorageFoundation<?> foundation
		)
		{
			return this;
		}

	}
	
	/**
	 * Default validator: enforces the 2&nbsp;GiB load-buffer ceiling on the <b>resulting on-disk
	 * file</b> (not just on the chunk payload). The engine appends a {@code FileHeaderV1} and a
	 * trailing chunk-checksum record when checksums are emitting; a chunk sized just under
	 * {@code Integer.MAX_VALUE} would otherwise produce a file slightly over 2&nbsp;GiB, which is
	 * not reloadable into a single direct buffer.
	 * <p>
	 * The foundation wires this validator with {@code freshFileMetaReserve = calc.fileHeaderRecordLength()
	 * + calc.chunkChecksumRecordLength()} for emitting policies (or 0 for non-emitting), so the
	 * cap automatically tracks the configured checksum algorithm.
	 */
	public class MaxFileSize implements StorageDataChunkValidator, Provider, Provider2
	{
		private final long freshFileMetaReserve;

		/**
		 * Backwards-compatible constructor: assumes no meta-record overhead. Direct users who
		 * keep this constructor must accept that the resulting file may exceed 2&nbsp;GiB by the
		 * meta-record bytes the engine appends.
		 */
		public MaxFileSize()
		{
			this(0L);
		}

		/**
		 * @param freshFileMetaReserve bytes the engine adds to a fresh file beyond the chunk
		 *                             payload (typically {@code FileHeaderV1 + ChunkChecksumV1}),
		 *                             subtracted from the 2&nbsp;GiB load-buffer ceiling so the
		 *                             resulting file remains reloadable.
		 */
		public MaxFileSize(final long freshFileMetaReserve)
		{
			super();
			this.freshFileMetaReserve = freshFileMetaReserve;
		}

		@Override
		public Provider provideDataChunkValidatorProvider(
				final StorageFoundation<?> foundation
		)
		{
			return this;
		}

		@Override
		public StorageDataChunkValidator provideDataChunkValidator(
				final StorageTypeDictionary typeDictionary
		)
		{
			return this;
		}

		@Override
		public final void validateDataChunk(
			final Binary data
		)
		{
			for(int channelIndex = 0; channelIndex < data.channelCount(); channelIndex++)
			{
				long commitSize = 0;
				for(ByteBuffer bb:data.channelChunk(channelIndex).buffers())
				{
					commitSize += bb.limit();
				}

				// The resulting file (chunk + FileHeaderV1 + trailing chunk-checksum) must fit
				// in a 2 GiB direct buffer so it remains loadable on the read path.
				if(commitSize + this.freshFileMetaReserve >= Integer.MAX_VALUE)
				{
					throw new StorageExceptionCommitSizeExceeded(
						channelIndex, commitSize + this.freshFileMetaReserve);
				}

			}
		}
	}

	/**
	 * Strict pre-flight cap: rejects a commit when any channel's chunk plus its fresh-file meta
	 * overhead ({@code FileHeaderV1} + trailing {@code ChunkChecksumV1}, when applicable) would
	 * inherently overshoot the configured {@code fileMaximumSize}. Runs on the caller thread
	 * before the task is enqueued, so the channel thread never sees a chunk that cannot fit into
	 * a fresh head file under the configured cap.
	 * <p>
	 * Opt-in: users who want {@code fileMaximumSize} as a hard upper bound on raw file size
	 * register this validator via
	 * {@link StorageFoundation#setDataChunkValidatorProvider2(Provider2)}. The default
	 * provider remains {@link MaxFileSize} (Java 2&nbsp;GiB load-buffer cap only); existing
	 * storages and BLOB-style users storing entities larger than {@code fileMaximumSize}
	 * continue to work unchanged.
	 * <p>
	 * Composes with {@link MaxFileSize}: this validator also enforces the 2&nbsp;GiB cap so it
	 * is a strict superset.
	 */
	public final class StrictMaxFileSize implements StorageDataChunkValidator, Provider, Provider2
	{
		private final long fileMaximumSize     ;
		private final long freshFileMetaReserve;

		/**
		 * @param fileMaximumSize      the configured maximum data file size; commits exceeding this
		 *                             per channel are rejected.
		 * @param freshFileMetaReserve the total number of meta bytes that a fresh head file pays for
		 *                             the upcoming chunk write: the {@code FileHeaderV1} written at
		 *                             file creation plus the trailing chunk-checksum record. Typically
		 *                             {@code calc.fileHeaderRecordLength() + calc.chunkChecksumRecordLength()}
		 *                             for emitting policies, or {@code 0} for non-emitting policies.
		 *                             The validator runs caller-side without per-file state, so it
		 *                             must reserve for the worst case: a chunk landing in a fresh
		 *                             head file that has just paid the header and will append the
		 *                             chunk plus its trailing checksum.
		 */
		public StrictMaxFileSize(final long fileMaximumSize, final long freshFileMetaReserve)
		{
			super();
			this.fileMaximumSize      = fileMaximumSize     ;
			this.freshFileMetaReserve = freshFileMetaReserve;
		}

		@Override
		public Provider provideDataChunkValidatorProvider(final StorageFoundation<?> foundation)
		{
			return this;
		}

		@Override
		public StorageDataChunkValidator provideDataChunkValidator(final StorageTypeDictionary typeDictionary)
		{
			return this;
		}

		@Override
		public void validateDataChunk(final Binary data)
		{
			for(int channelIndex = 0; channelIndex < data.channelCount(); channelIndex++)
			{
				long commitSize = 0;
				for(final ByteBuffer bb : data.channelChunk(channelIndex).buffers())
				{
					commitSize += bb.limit();
				}

				if(commitSize + this.freshFileMetaReserve >= Integer.MAX_VALUE)
				{
					// also enforce the 2 GiB load-buffer cap so this validator is a superset of MaxFileSize
					throw new StorageExceptionCommitSizeExceeded(channelIndex, commitSize + this.freshFileMetaReserve);
				}
				if(commitSize + this.freshFileMetaReserve > this.fileMaximumSize)
				{
					// chunk + fresh-file meta (FileHeaderV1 + trailing chunk-checksum) cannot fit
					// in any single file under the configured cap.
					throw new StorageExceptionCommitSizeExceeded(channelIndex, commitSize + this.freshFileMetaReserve);
				}
			}
		}
	}

}
