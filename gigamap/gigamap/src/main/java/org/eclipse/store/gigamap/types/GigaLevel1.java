package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.persistence.types.Unpersistable;
import org.eclipse.serializer.reflect.XReflect;


/**
 * GigaLevel1 represents a generic container that manages an array of entities.
 * It extends {@link AbstractStateChangeFlagged} to incorporate state change tracking
 * capabilities but functions as a leaf node in the state hierarchy, meaning it
 * does not track state changes for children since it does not conceptually
 * contain any.
 * <p>
 * This class provides functionality to initialize and manage a typed array of
 * entities. It ensures safe type handling during runtime via instantiation-specific
 * operations despite type erasure.
 *
 * @param <E> The type of elements stored in this container.
 */
public final class GigaLevel1<E> extends AbstractStateChangeFlagged implements Unpersistable
{
	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////
	
	static BinaryTypeHandler<GigaLevel1<?>> provideTypeHandler()
	{
		return BinaryHandlerGigaLevel1.New();
	}
	
	@SuppressWarnings("unchecked")
	private E[] createEntitiesArray(final int length)
	{
		return (E[])new Object[length];
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	E[] entities;

	/**
	 * What the last committed store wrote into this segment's persisted reference list, or
	 * {@literal null} for a segment that keeps no such record.
	 * <p>
	 * Storing a segment writes every slot's reference, so without this an unchanged entity would be
	 * assigned a new object id and stored again on every store of the segment. That is invisible for
	 * an entity with identity, which the object registry answers for, but not for an identity-less
	 * one: it has no registry entry, so every store would leave a superseded copy behind.
	 * <p>
	 * Transient and only ever an optimization: a missing record merely means the entity is stored
	 * again.
	 *
	 * @see StoredState
	 */
	transient StoredState storedState;

	// identity-less entities can only exist where value classes are enabled, see #storedState.
	private static final boolean VALUE_CLASSES_ENABLED = XReflect.isValueClassEnabledRuntime();



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	GigaLevel1(final int length, final boolean newInstance)
	{
		super(newInstance);
		this.entities = this.createEntitiesArray(length);
	}



	///////////////////////////////////////////////////////////////////////////
	// stored state //
	/////////////////

	/**
	 * The element written into each slot of the persisted reference list, and the object id it was
	 * written under. Held as one instance so a store hands over both halves at once: they are only
	 * meaningful together, an id describing the element it was written for and nothing else.
	 */
	static final class StoredState
	{
		final Object[] entities ;
		final long[]   objectIds;

		StoredState(final Object[] entities, final long[] objectIds)
		{
			super();
			this.entities  = entities ;
			this.objectIds = objectIds;
		}

		StoredState copy()
		{
			return new StoredState(this.entities.clone(), this.objectIds.clone());
		}
	}

	/**
	 * Whether any slot holds an entity without identity, which is the only case a stored state pays
	 * off for - and the reason it is asked per segment instead of per JVM: once value classes are
	 * enabled everywhere, a JVM-wide answer would burden every GigaMap of ordinary entities with it.
	 */
	private boolean holdsIdentitylessEntity()
	{
		if(!VALUE_CLASSES_ENABLED)
		{
			return false;
		}

		// entities of one segment are typically of one type, so remembering the last answer suffices
		Class<?> identityType = null;
		for(final E entity : this.entities)
		{
			if(entity == null)
			{
				continue;
			}
			final Class<?> type = entity.getClass();
			if(type == identityType)
			{
				continue;
			}
			if(XReflect.isValueClass(type))
			{
				return true;
			}
			identityType = type;
		}

		return false;
	}

	/**
	 * The arrays a store writes its record into: a copy of the current one, so an uncommitted store
	 * never changes what the persisted segment is described by, or a blank one where there is none
	 * yet. A blank record matches no slot, so every entity is applied.
	 *
	 * @return the record to be filled, or {@literal null} for a segment that keeps none.
	 */
	final StoredState newStoreRecord()
	{
		final StoredState current = this.storedState;
		if(current != null)
		{
			return current.copy();
		}

		return this.holdsIdentitylessEntity()
			? new StoredState(new Object[this.entities.length], new long[this.entities.length])
			: null
		;
	}

	/**
	 * The array a load collects the persisted object ids into, or {@literal null} for a segment that
	 * keeps no record.
	 */
	final long[] newLoadRecordObjectIds()
	{
		return this.holdsIdentitylessEntity()
			? new long[this.entities.length]
			: null
		;
	}

	/**
	 * Takes over a record of what the persisted segment holds, from either of the two things that can
	 * know it: a store, once it was committed, or a load, which read it.
	 * <p>
	 * A store may only hand its record over after committing. A failed one leaves the persisted segment
	 * referencing the previous record, so the ids it assigned would reference entities that were never
	 * written.
	 * <p>
	 * What this does not answer is which of two stores of the same segment committed last: commit
	 * listeners fire per commit, in no order relative to another store's write. The per-slot element
	 * comparison catches a slot written between store and commit, not a competing store of the whole
	 * segment - unchanged from before this record existed, and outside what {@link GigaMap#store()}
	 * promises. A record that lost that race describes entities the persisted segment does not
	 * reference, which the store-time reference validation rejects rather than commits.
	 *
	 * @param storedState the record of what the persisted segment holds.
	 */
	final void adoptStoredState(final StoredState storedState)
	{
		this.storedState = storedState;
	}



	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	/**
	 * Whether this segment currently holds no entity at all, which makes it eligible for being released
	 * from its parent {@link GigaLevel2}.
	 * <p>
	 * {@code scanStartIndex} only steers where the scan begins, it does not narrow what is examined:
	 * every slot is checked, ending with {@code scanStartIndex} itself, so the answer never depends on
	 * the caller having cleared that slot. Starting right after the slot a removal just cleared is what
	 * makes a sequential drain, forward or backward, hit a surviving neighbour on the very first
	 * comparison instead of walking the whole array.
	 * <p>
	 * Bound to the array's own length rather than to the parent map's configured segment size, simply
	 * because this class owns the array and needs no state of the map to walk it. The two always agree:
	 * the length exponents are persisted with the map and a reloaded map is constructed from those, so
	 * a reloaded array's length is the configured segment size.
	 *
	 * @param scanStartIndex the index to start scanning after, typically the slot that was cleared last
	 * @return {@code true} if every slot is {@code null}, {@code false} otherwise
	 */
	final boolean isEmpty(final int scanStartIndex)
	{
		final E[] entities = this.entities;
		final int length   = entities.length;

		for(int i = 1; i <= length; i++)
		{
			int index = scanStartIndex + i;
			if(index >= length)
			{
				index -= length;
			}
			if(entities[index] != null)
			{
				return false;
			}
		}

		return true;
	}

	@Override
	protected void storeChangedChildren(final Storer storer)
	{
		// GigaLevel1 may never be marked as having children changed since is it a leaf instance. Not perfectly clean.
		throw new UnsupportedOperationException();
	}
	
	@Override
	protected void clearChildrenStateChangeMarkers()
	{
		// no-op since there are no state-change-marked children.
	}
	
}
