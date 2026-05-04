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

import static org.eclipse.serializer.util.X.notNull;

import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.exceptions.PersistenceExceptionTransfer;
import org.eclipse.serializer.persistence.types.PersistenceTarget;
import org.eclipse.store.storage.types.StorageRequestAcceptor;
import org.eclipse.store.storage.types.StorageWriteController;

/**
 * {@link PersistenceTarget} specialization that hands persisted binary data over to an embedded storage
 * instance.
 * <p>
 * The target acts as the write side of the bridge between the persistence layer and the storage threads:
 * each call to {@link #write(Binary)} is first checked against a {@link StorageWriteController} (which can,
 * for example, deny writes during a read-only phase or backup) and then forwarded to a
 * {@link StorageRequestAcceptor} that dispatches the data to the storage channels for persistence.
 *
 * @see EmbeddedStorageBinarySource
 * @see PersistenceTarget
 */
public interface EmbeddedStorageBinaryTarget extends PersistenceTarget<Binary>
{
	/**
	 * Persists the passed binary data.
	 * <p>
	 * The implementation first validates that writing is currently permitted via the associated
	 * {@link StorageWriteController}, then forwards the data to the underlying storage threads.
	 *
	 * @param data the binary chunk to be stored.
	 *
	 * @throws PersistenceExceptionTransfer if writing is currently not permitted or if the underlying
	 *         storage layer fails to persist the data.
	 */
	@Override
	public void write(Binary data) throws PersistenceExceptionTransfer;



	/**
	 * Pseudo-constructor method to create a new {@link EmbeddedStorageBinaryTarget} instance.
	 *
	 * @param requestAcceptor the {@link StorageRequestAcceptor} used to dispatch store requests to the
	 *        storage threads. May not be {@code null}.
	 *
	 * @param writeController the {@link StorageWriteController} used to gate write requests. May not be
	 *        {@code null}.
	 *
	 * @return a new {@link EmbeddedStorageBinaryTarget} instance.
	 */
	public static EmbeddedStorageBinaryTarget New(
		final StorageRequestAcceptor requestAcceptor,
		final StorageWriteController writeController
	)
	{
		return new EmbeddedStorageBinaryTarget.Default(
			notNull(requestAcceptor),
			notNull(writeController)
		);
	}

	/**
	 * Default implementation of {@link EmbeddedStorageBinaryTarget} that consults its
	 * {@link StorageWriteController} on every write and delegates the actual persistence work to a
	 * {@link StorageRequestAcceptor}.
	 */
	public final class Default implements EmbeddedStorageBinaryTarget
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final StorageRequestAcceptor requestAcceptor;
		private final StorageWriteController writeController;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final StorageRequestAcceptor requestAcceptor,
			final StorageWriteController writeController
		)
		{
			super();
			this.requestAcceptor = requestAcceptor;
			this.writeController = writeController;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final void write(final Binary data) throws PersistenceExceptionTransfer
		{
			try
			{
				this.writeController.validateIsWritable();
				this.requestAcceptor.storeData(data);
			}
			catch(final Exception e)
			{
				throw new PersistenceExceptionTransfer(e);
			}
		}
		
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
			this.writeController.validateIsStoringEnabled();
		}
		
		@Override
		public final boolean isStoringEnabled()
		{
			return this.writeController.isStoringEnabled();
		}

	}

}
