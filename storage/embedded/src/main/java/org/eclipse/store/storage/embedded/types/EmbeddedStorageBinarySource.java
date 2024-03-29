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

import org.eclipse.serializer.collections.ArrayView;
import org.eclipse.serializer.collections.types.XGettingCollection;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.exceptions.PersistenceExceptionTransfer;
import org.eclipse.serializer.persistence.types.PersistenceIdSet;
import org.eclipse.serializer.persistence.types.PersistenceSource;
import org.eclipse.store.storage.types.StorageRequestAcceptor;


public interface EmbeddedStorageBinarySource extends PersistenceSource<Binary>
{
	@Override
	public XGettingCollection<? extends Binary> read() throws PersistenceExceptionTransfer;

	@Override
	public XGettingCollection<? extends Binary> readByObjectIds(PersistenceIdSet[] oids)
		throws PersistenceExceptionTransfer;



	public final class Default implements EmbeddedStorageBinarySource
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final StorageRequestAcceptor requestAcceptor;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		public Default(final StorageRequestAcceptor requestAcceptor)
		{
			super();
			this.requestAcceptor = requestAcceptor;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public XGettingCollection<? extends Binary> read() throws PersistenceExceptionTransfer
		{
			try
			{
				return new ArrayView<>(this.requestAcceptor.recallRoots());
			}
			catch(final InterruptedException e)
			{
				throw new PersistenceExceptionTransfer(e);
				/* Not sure if this is the best way to handle the interruption, as it swallows the interruption
				 * on the semantic level and requires call site cause analysis to recognize it.
				 *
				 * Sadly, due to the checked exception concept, the interruption cannot be propagated to the
				 * calling context, not even declared to be made visible to the using developer.
				 * Being able to be validly interrupted is an implementation detail that cannot be declared in the
				 * abstract interface declaring this method. If one would "cleanly" follow the concept of
				 * checked exceptions, in the end half of all methods would have to declare countless checked exceptions
				 * that won't occur in most implementation cases (e.g. see JDBC driver methods or reflection)
				 * The misconception can also be seen easily on unchecked JDK exceptions that are actually reasonably to
				 * recover from but still are not checked exceptions because they would mess up the whole API, like
				 * IllegalArgumentException.
				 * The only proper way is to propagate unchecked exceptions of the API level and then
				 * handle them by design (not by compiler) where necessary, using exception declaration only as a
				 * hint, not as a rule.
				 */
			}
		}

		@Override
		public XGettingCollection<? extends Binary> readByObjectIds(final PersistenceIdSet[] oids)
			throws PersistenceExceptionTransfer
		{
			try
			{
				return new ArrayView<>(this.requestAcceptor.queryByObjectIds(oids));
			}
			catch(final InterruptedException e)
			{
				throw new PersistenceExceptionTransfer(e);
				/* Not sure if this is the best way to handle the interruption, as it swallows the interruption
				 * on the semantic level and requires call site cause analysis to recognize it.
				 *
				 * Sadly, due to the checked exception concept, the interruption cannot be propagated to the
				 * calling context, not even declared to be made visible to the using developer.
				 * Being able to be validly interrupted is an implementation detail that cannot be declared in the
				 * abstract interface declaring this method. If one would "cleanly" follow the concept of
				 * checked exceptions, in the end half of all methods would have to declare countless checked exceptions
				 * that won't occur in most implementation cases (e.g. see JDBC driver methods or reflection)
				 * The misconception can also be seen easily on unchecked JDK exceptions that are actually reasonably to
				 * recover from but still are not checked exceptions because they would mess up the whole API, like
				 * IllegalArgumentException.
				 * The only proper way is to propagate unchecked exceptions of the API level and then
				 * handle them by design (not by compiler) where necessary, using exception declaration only as a
				 * hint, not as a rule.
				 */
			}
		}

	}

}
