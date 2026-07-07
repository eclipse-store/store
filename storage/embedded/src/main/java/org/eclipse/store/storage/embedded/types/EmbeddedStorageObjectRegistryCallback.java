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

import org.eclipse.serializer.persistence.types.*;
import org.eclipse.store.storage.types.LiveObjectIdsHandler;

/**
 * Callback bridge between the storage layer's garbage collector and the persistence layer's
 * {@link PersistenceObjectRegistry}.
 * <p>
 * The storage GC needs to know which object ids are currently held alive on the application side so that it
 * does not erroneously delete the corresponding entities. As a {@link LiveObjectIdsHandler} this callback
 * serves the GC's two interaction phases against the application registry:
 * <ul>
 *   <li><b>Mark seeding</b> via {@link #iterateLiveObjectIds(PersistenceObjectIdAcceptor)} — at the start of a
 *       mark cycle the GC enumerates the application-held data object ids to seed mark roots, so that
 *       entities reachable only through application-held references have their binary references walked and
 *       transitively kept alive.</li>
 *   <li><b>Sweep filter</b> via {@link #processSelected(ObjectIdsProcessor)} — during sweep the GC asks the
 *       registry "is this object id still held by the application?" as the final safety net against
 *       erroneous deletion.</li>
 *   <li><b>Registration-version publication</b> via {@link #registrationVersion()} — a passive third role:
 *       the GC snapshots the registry's registration version at every mark seed and re-arms the seed before
 *       initiating a sweep if registrations happened in between (mid-cycle registration race).</li>
 * </ul>
 * In embedded mode the persistence layer that owns those live object ids is created later than the storage
 * layer, so this callback is registered with the storage upfront and is then
 * {@link #initializeObjectRegistry(PersistenceObjectRegistry) initialized} as soon as the application-side
 * {@link PersistenceObjectRegistry} becomes available.
 * <p>
 * Until initialization the callback reports no live object ids — {@link #processSelected processSelected}
 * applies an empty filter and {@link #iterateLiveObjectIds iterateLiveObjectIds} is a no-op — which is a safe
 * default during the brief window between storage startup and the creation of the first storage connection.
 *
 * @see LiveObjectIdsHandler
 * @see PersistenceObjectRegistry
 */
public interface EmbeddedStorageObjectRegistryCallback extends LiveObjectIdsHandler
{
	/**
	 * Binds the passed {@link PersistenceObjectRegistry} as the source of live object ids reported via
	 * {@link #processSelected(ObjectIdsProcessor)} and {@link #iterateLiveObjectIds(PersistenceObjectIdAcceptor)}.
	 * <p>
	 * Calling this method again with the same registry instance is a no-op; calling it with a different
	 * registry instance is rejected to avoid silently switching the live id source under a running garbage
	 * collector.
	 *
	 * @param objectRegistry the {@link PersistenceObjectRegistry} whose live object ids shall be exposed.
	 */
	public void initializeObjectRegistry(PersistenceObjectRegistry objectRegistry);



	/**
	 * Pseudo-constructor method to create a new, uninitialized {@link EmbeddedStorageObjectRegistryCallback}
	 * instance.
	 *
	 * @return a new {@link EmbeddedStorageObjectRegistryCallback} instance.
	 */
	public static EmbeddedStorageObjectRegistryCallback New()
	{
		return new EmbeddedStorageObjectRegistryCallback.Default();
	}

	/**
	 * Default implementation that reports no live object ids until
	 * {@link #initializeObjectRegistry(PersistenceObjectRegistry)} has been called, and afterwards forwards
	 * both the sweep-time selection and the mark-time iteration to the bound {@link PersistenceObjectRegistry}.
	 * <p>
	 * The mark-seed iteration emits every data-OID entry present in the registry's hash table regardless of
	 * whether the underlying {@link java.lang.ref.WeakReference} is still live, mirroring the id-only
	 * keep-alive predicate applied during sweep. {@link Persistence.IdType#OID OID}s only — TypeIds and
	 * ConstantIds are skipped because they are intentionally unresolvable as storage entities and would
	 * otherwise trigger the zombie handler.
	 * <p>
	 * All public methods are {@code synchronized} on the instance to coordinate the late initialization with
	 * concurrent GC callbacks.
	 */
	public final class Default implements EmbeddedStorageObjectRegistryCallback
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private PersistenceObjectRegistry objectRegistry;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default()
		{
			super();
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public synchronized void initializeObjectRegistry(final PersistenceObjectRegistry objectRegistry)
		{
			if(this.objectRegistry != null)
			{
				if(this.objectRegistry == objectRegistry)
				{
					return;
				}

				// (29.07.2022 TM)EXCP: proper exception
				throw new RuntimeException("ObjectRegistry already initialized.");
			}

			this.objectRegistry = objectRegistry;
		}

		@Override
		public synchronized boolean processSelected(final ObjectIdsProcessor processor)
		{
			if(this.objectRegistry == null)
			{
				// object registry not yet initialized (i.e. no application-side storage connection yet)
				processor.processObjectIdsByFilter(objectId -> false);
				return true;
			}

			// efficient for embedded mode, but server mode should use #selectLiveObjectIds instead.
			return this.objectRegistry.processLiveObjectIds(processor);
		}

		@Override
		public synchronized long registrationVersion()
		{
			return this.objectRegistry == null
				? 0L
				: this.objectRegistry.registrationVersion()
			;
		}

		@Override
		public synchronized void iterateLiveObjectIds(final PersistenceObjectIdAcceptor acceptor)
		{
			if(this.objectRegistry == null)
			{
				return;
			}

			this.objectRegistry.iterateEntries((objectId, instance) ->
			{
				// Emit every data-OID entry in the hash table, regardless of whether the
				// WeakReference is still live. The sweep keep-alive predicate
				// (DefaultObjectRegistry#synchIsLiveObjectId -> synchContainsObjectId) is id-only and
				// keeps cleared-but-not-yet-reaped entries alive at sweep time; if mark seeding
				// skipped them here, their stored binary references would not be walked, and
				// transitively-reachable entities whose own entries were already reaped could be
				// swept while the parent stays kept — producing a zombie OID on the next mark cycle.
				// Skip TypeIds and ConstantIds though: those are intentionally unresolvable as
				// storage entities and would trigger the zombie handler.
				if(Persistence.IdType.OID.isInRange(objectId))
				{
					acceptor.acceptObjectId(objectId);
				}
			});
		}

	}

}
