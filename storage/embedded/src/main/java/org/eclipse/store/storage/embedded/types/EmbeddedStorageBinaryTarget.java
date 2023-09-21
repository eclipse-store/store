package org.eclipse.store.storage.embedded.types;

/*-
 * #%L
 * EclipseStore Storage Embedded
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import static org.eclipse.serializer.util.X.notNull;

import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.exceptions.PersistenceExceptionTransfer;
import org.eclipse.serializer.persistence.types.PersistenceTarget;
import org.eclipse.store.storage.types.StorageRequestAcceptor;
import org.eclipse.store.storage.types.StorageWriteController;

public interface EmbeddedStorageBinaryTarget extends PersistenceTarget<Binary>
{
	@Override
	public void write(Binary data) throws PersistenceExceptionTransfer;


	
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
