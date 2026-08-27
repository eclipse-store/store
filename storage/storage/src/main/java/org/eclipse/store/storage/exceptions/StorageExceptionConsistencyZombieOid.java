package org.eclipse.store.storage.exceptions;

/*-
 * #%L
 * EclipseStore Storage
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

/**
 * Thrown by the strict {@link org.eclipse.store.storage.types.StorageGCZombieOidHandler} when the
 * storage garbage collector's marking encounters a reference to a data object id that does not
 * resolve to an entity &mdash; a "zombie" object id. A zombie means some persisted binary record
 * references an entity that no longer exists: the storage already contains a dangling reference
 * that would otherwise surface only as a
 * {@code StorageExceptionConsistency: No entity found for objectId} on a later load or restart,
 * typically after housekeeping has destroyed the evidence.
 * <p>
 * Failing at mark time halts the affected storage channel while the swept entity's bytes may
 * still be physically present in the data files (the startup scanner can resurrect uncompacted
 * entities), maximizing the chance of diagnosis and recovery. This is a deliberate, opt-in
 * diagnostic trade-off: availability is sacrificed for data-integrity visibility.
 */
public class StorageExceptionConsistencyZombieOid extends StorageExceptionConsistency
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final long objectId;



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public StorageExceptionConsistencyZombieOid(final long objectId)
	{
		super(
			"Storage GC marking encountered zombie objectId " + objectId
			+ ": a persisted binary record references an entity that does not exist (dangling reference"
			+ " already present in the storage). Failing now preserves the best chance of diagnosis and"
			+ " recovery before housekeeping physically reclaims related data."
		);
		this.objectId = objectId;
	}



	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	/**
	 * @return the unresolvable data object id encountered by the garbage collector's marking.
	 */
	public long objectId()
	{
		return this.objectId;
	}

}
