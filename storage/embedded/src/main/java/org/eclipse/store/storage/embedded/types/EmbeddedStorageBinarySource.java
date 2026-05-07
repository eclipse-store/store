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

import java.util.function.Supplier;

import org.eclipse.serializer.collections.ArrayView;
import org.eclipse.serializer.collections.types.XGettingCollection;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.exceptions.PersistenceExceptionTransfer;
import org.eclipse.serializer.persistence.types.PersistenceIdSet;
import org.eclipse.serializer.persistence.types.PersistenceSource;
import org.eclipse.store.storage.types.StorageRequestAcceptor;


/**
 * {@link PersistenceSource} specialization that supplies persisted binary data from an embedded storage instance
 * to the persistence layer.
 * <p>
 * The source acts as the read side of the bridge between the persistence layer and the storage threads:
 * it forwards every read request to a {@link StorageRequestAcceptor}, which dispatches the request to the
 * storage channels and returns the resulting binary chunks to the calling persistence loader.
 *
 * @see EmbeddedStorageBinaryTarget
 * @see PersistenceSource
 */
public interface EmbeddedStorageBinarySource extends PersistenceSource<Binary>
{
	/**
	 * Reads the data of all root entities from the storage.
	 * <p>
	 * Used during startup to recall the persistent root graph; subsequent reachable entities are then
	 * loaded on demand via {@link #readByObjectIds(PersistenceIdSet[])}.
	 *
	 * @return the binary chunks containing the root entities' data.
	 *
	 * @throws PersistenceExceptionTransfer if the read request could not be completed (e.g. due to thread
	 *         interruption or an underlying storage error).
	 */
	@Override
	public XGettingCollection<? extends Binary> read() throws PersistenceExceptionTransfer;

	/**
	 * Reads the data of the entities identified by the passed object ids.
	 * <p>
	 * The passed array is indexed by storage channel: each {@link PersistenceIdSet} contains the object ids
	 * to be loaded by the channel of the same index.
	 *
	 * @param oids the channel-partitioned object ids to load.
	 *
	 * @return the binary chunks containing the requested entities' data.
	 *
	 * @throws PersistenceExceptionTransfer if the read request could not be completed (e.g. due to thread
	 *         interruption or an underlying storage error).
	 */
	@Override
	public XGettingCollection<? extends Binary> readByObjectIds(PersistenceIdSet[] oids)
		throws PersistenceExceptionTransfer;



	/**
	 * Default implementation of {@link EmbeddedStorageBinarySource} that delegates each read request to a
	 * {@link StorageRequestAcceptor} and translates any thrown {@link InterruptedException} into a
	 * {@link PersistenceExceptionTransfer}.
	 */
	public final class Default implements EmbeddedStorageBinarySource
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		/*
		 * Holds the current {@link StorageRequestAcceptor} via a {@link Supplier} rather than a
		 * direct reference, so a {@code shutdown()/start()} cycle (which replaces the storage
		 * system's task broker and therefore the acceptor) is observed automatically on the
		 * next call. No update method, no rebind step, no explicit shutdown hook required.
		 */
		private final Supplier<StorageRequestAcceptor> requestAcceptorSupplier;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		/**
		 * Constructs a Source that resolves the current {@link StorageRequestAcceptor} from the
		 * given supplier on every read. The supplier is expected to return the storage's
		 * currently active acceptor, so the Source self-heals across shutdown/start cycles.
		 *
		 * @param requestAcceptorSupplier supplier of the currently-active request acceptor.
		 */
		public Default(final Supplier<StorageRequestAcceptor> requestAcceptorSupplier)
		{
			super();
			this.requestAcceptorSupplier = requestAcceptorSupplier;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public XGettingCollection<? extends Binary> read() throws PersistenceExceptionTransfer
		{
			try
			{
				return new ArrayView<>(this.requestAcceptorSupplier.get().recallRoots());
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
				return new ArrayView<>(this.requestAcceptorSupplier.get().queryByObjectIds(oids));
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
