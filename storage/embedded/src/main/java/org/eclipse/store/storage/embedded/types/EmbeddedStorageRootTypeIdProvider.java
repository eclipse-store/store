package org.eclipse.store.storage.embedded.types;

/*-
 * #%L
 * EclipseStore Storage Embedded
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

import static org.eclipse.serializer.math.XMath.positive;

import org.eclipse.serializer.persistence.types.PersistenceTypeManager;
import org.eclipse.store.storage.exceptions.StorageExceptionInitialization;
import org.eclipse.store.storage.types.StorageRootTypeIdProvider;

/**
 * {@link StorageRootTypeIdProvider} specialization for embedded storage that resolves the type id of the
 * persistent roots holder type lazily.
 * <p>
 * In embedded mode the persistent roots holder type id is not known upfront, since the persistence type
 * dictionary is built up while the {@link EmbeddedStorageManager} starts. Instances of this provider therefore
 * remember the root {@link Class} until {@link #initialize(PersistenceTypeManager)} is called from the startup
 * sequence; from that point on, {@link #provideRootTypeId()} returns the resolved type id.
 *
 * @see StorageRootTypeIdProvider
 */
public interface EmbeddedStorageRootTypeIdProvider extends StorageRootTypeIdProvider
{
	/**
	 * Resolves the type id of the configured root type via the passed {@link PersistenceTypeManager} and caches
	 * it for subsequent calls to {@link #provideRootTypeId()}.
	 * <p>
	 * Must be called exactly once during storage startup, after the persistence type handlers have been
	 * initialized.
	 *
	 * @param typeIdResolver the {@link PersistenceTypeManager} used to look up or assign the root type id.
	 */
	public void initialize(PersistenceTypeManager typeIdResolver);



	/**
	 * Pseudo-constructor method to create a new {@link EmbeddedStorageRootTypeIdProvider} for the given root
	 * holder type.
	 *
	 * @param rootType the {@link Class} of the persistent roots holder whose type id is to be provided.
	 *
	 * @return a new {@link EmbeddedStorageRootTypeIdProvider} instance.
	 */
	public static EmbeddedStorageRootTypeIdProvider New(final Class<?> rootType)
	{
		return new EmbeddedStorageRootTypeIdProvider.Default(rootType);
	}

	/**
	 * Default implementation that caches the resolved root type id after {@link #initialize(PersistenceTypeManager)}
	 * has been invoked and throws a {@link StorageExceptionInitialization} when {@link #provideRootTypeId()} is
	 * called before initialization.
	 */
	public final class Default implements EmbeddedStorageRootTypeIdProvider
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final Class<?> rootType;

		private transient Long cachedRootTypeId;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(final Class<?> rootType)
		{
			super();
			this.rootType = rootType;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final long provideRootTypeId()
		{
			if(this.cachedRootTypeId == null)
			{
				throw new StorageExceptionInitialization("not initialized");
			}
			return this.cachedRootTypeId;
		}

		@Override
		public final void initialize(final PersistenceTypeManager typeIdResolver)
		{
			final long typeId = typeIdResolver.ensureTypeId(this.rootType);
			this.cachedRootTypeId = positive(typeId);
		}

	}

}
