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

import org.eclipse.serializer.persistence.types.PersistenceObjectIdAcceptor;

/**
 * Enumerates every application-held object id and pushes each one to the given acceptor. Used by
 * the storage GC to seed mark roots from the application's object registry so that
 * safety-net-kept entities have their binary references walked transitively, preventing zombie
 * OIDs caused by stale binary records referencing swept entities.
 * <p>
 * The enumerated set must match the id-only criterion used by the sweep keep-alive predicate
 * (see {@link org.eclipse.serializer.persistence.types.ObjectIdsSelector}): every data-OID entry
 * present in the registry's hash table is emitted, regardless of whether the underlying
 * {@link java.lang.ref.WeakReference} is still live. If the mark seed skipped entries whose
 * {@code WeakReference} has been cleared but not yet reaped while the sweep predicate kept them
 * alive, their stored binary references would not be walked and transitively-reachable entities
 * could be swept under a still-kept parent — producing zombie OIDs on the next mark cycle.
 */
@FunctionalInterface
public interface LiveObjectIdsIterator
{
	/**
	 * Pushes every application-held data object id into {@code acceptor}, with the id-only
	 * inclusion criterion described on the {@linkplain LiveObjectIdsIterator type}.
	 *
	 * @param acceptor receives one {@code acceptObjectId} call per emitted id; typically the
	 *                 storage mark monitor, which routes ids into per-channel mark queues.
	 */
	public void iterateLiveObjectIds(PersistenceObjectIdAcceptor acceptor);

	/**
	 * The application object registry's registration version (see
	 * {@link org.eclipse.serializer.persistence.types.PersistenceObjectRegistry#registrationVersion()}):
	 * changes whenever a new object id association is inserted. The storage GC snapshots this at
	 * every live-OID mark seed and re-arms the seed before initiating a sweep if the version moved
	 * — otherwise an entity registered <i>after</i> the seed but <i>before</i> the sweep would be
	 * rescued only shallowly, and entities referenced solely from its persisted binary (e.g. an
	 * unloaded lazy reference's target) would be swept (mid-cycle registration race).
	 * <p>
	 * The default returns a constant {@code 0}, meaning "registrations are never observable" —
	 * implementations backed by a runtime-mutable registry must override this.
	 *
	 * @return the current registration version.
	 */
	public default long registrationVersion()
	{
		return 0L;
	}
}
