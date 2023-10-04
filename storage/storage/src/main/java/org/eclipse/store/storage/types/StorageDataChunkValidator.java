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

import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.binary.types.BinaryEntityRawDataIterator;


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

}
