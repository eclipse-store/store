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

import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistencyZombieOid;

/**
 * Note on zombie OID / null entry during GC:
 * This should of course never happen and must be seen as a bug.
 * However the GC is not necessarily the place to break in such a case.
 * Currently, this can even happen regularly, if the last reference to an entity is removed, then the GC
 * deleted the entity and then a store reestablishes the reference to the then deleted entity.
 * This might be a bug in the user code or in the storer or object registry or whatever, but it should
 * be recognized and handled at that point, not break the GC.
 * For that reason, handling an encountered zombie OID is modularized with the default of ignoring it
 * ({@link #New()}: WARN log + {@link StorageEventLogger} event, GC continues). For deployments where a
 * dangling reference must be caught while the evidence is still recoverable, {@link #Strict()} throws
 * instead, halting the affected channel (see the {@code gc-zombie-oid-handling} configuration property).
 *
 * Note that ConstantIds for JLS constants and TypeIds are intentionally unresolvable in the persistent state.
 * @see Persistence.IdType#TID
 * @see Persistence.IdType#CID
 *
 */
@FunctionalInterface
public interface StorageGCZombieOidHandler
{
	public boolean handleZombieOid(long objectId);


	/**
	 * Pseudo-constructor method to create the tolerating default handler: encountered data-OID zombies
	 * are reported by the caller (WARN log + event) and the garbage collection continues.
	 *
	 * @return a new tolerating {@link StorageGCZombieOidHandler}.
	 */
	public static StorageGCZombieOidHandler New()
	{
		return new StorageGCZombieOidHandler.Default();
	}

	/**
	 * Pseudo-constructor method to create the strict handler: an encountered data-OID zombie throws a
	 * {@link StorageExceptionConsistencyZombieOid}, halting the affected storage channel. This turns a
	 * silent dangling reference into an immediate, diagnosable failure while the swept entity's bytes
	 * may still be physically present (housekeeping has not yet reclaimed them) — a deliberate
	 * availability-for-integrity trade-off intended for diagnosis-focused deployments.
	 *
	 * @return a new strict {@link StorageGCZombieOidHandler}.
	 */
	public static StorageGCZombieOidHandler Strict()
	{
		return new StorageGCZombieOidHandler.Strict();
	}


	public final class Default implements StorageGCZombieOidHandler
	{
		@Override
		public final boolean handleZombieOid(final long objectId)
		{
			/*
			 * Note that types and constants are intentionally not represented in the persistent form
			 * but are resolved at runtime by the loading mechanism.
			 * It is NOT an error that these OIDs cannot be resolved on the persistent form level.
			 */
			if(Persistence.IdType.TID.isInRange(objectId))
			{
				// debug hook for TypeIDs
				return true;
			}
			if(Persistence.IdType.CID.isInRange(objectId))
			{
				// debug hook for ConstantIDs
				return true;
			}

			return false;
		}
	}

	/**
	 * Strict implementation: tolerates the intentionally unresolvable TypeIds and ConstantIds like
	 * {@link Default}, but throws a {@link StorageExceptionConsistencyZombieOid} for any data object id.
	 */
	public final class Strict implements StorageGCZombieOidHandler
	{
		@Override
		public final boolean handleZombieOid(final long objectId)
		{
			if(Persistence.IdType.TID.isInRange(objectId) || Persistence.IdType.CID.isInRange(objectId))
			{
				// intentionally unresolvable in the persistent state, see Default.
				return true;
			}

			throw new StorageExceptionConsistencyZombieOid(objectId);
		}
	}
}
