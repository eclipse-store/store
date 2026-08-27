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

import java.util.Arrays;

import org.eclipse.serializer.persistence.exceptions.PersistenceDanglingReferences;
import org.eclipse.serializer.util.X;

/**
 * Thrown when a store's data references object ids for which no entity exists in the storage &mdash;
 * dangling references. The referenced instances were trusted to already exist (they were found in the
 * global object registry or cached by unloaded {@code Lazy} references), but their entities are gone,
 * typically because the storage-level garbage collector reclaimed them after the last persisted
 * reference was removed while the instances survived in application memory.
 * <p>
 * The store was rejected atomically; the storage remains fully usable. To resolve, re-store the
 * referenced objects so their data exists again &mdash; e.g. include them explicitly in the store
 * ({@code storer.storeAll(parent, child)}) or store them with an eager storer &mdash; and retry.
 * With the {@code heal} validation policy, the storer performs that repair automatically and this
 * exception only surfaces when healing is impossible (no live instance for a missing id).
 * <p>
 * Note that at the caller, this exception is wrapped: it appears as the cause of a
 * {@code PersistenceExceptionTransfer} / {@code StorageException} chain.
 */
public class StorageExceptionConsistencyDanglingReference
extends StorageExceptionConsistency
implements PersistenceDanglingReferences
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final int    channelIndex    ;
	private final long[] missingObjectIds;



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public StorageExceptionConsistencyDanglingReference(
		final int    channelIndex    ,
		final long[] missingObjectIds
	)
	{
		// notNull: fail with a clear location instead of an NPE from message construction.
		super(buildMessage(channelIndex, X.notNull(missingObjectIds)));
		this.channelIndex     = channelIndex            ;
		// defensive copy: the exception state must stay immutable for later logging/inspection.
		this.missingObjectIds = missingObjectIds.clone();
	}



	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	/**
	 * @return the index of the channel that detected the dangling references.
	 */
	public int channelIndex()
	{
		return this.channelIndex;
	}

	/**
	 * @return the referenced object ids for which no entity exists (a defensive copy).
	 */
	@Override
	public long[] missingObjectIds()
	{
		return this.missingObjectIds.clone();
	}

	private static String buildMessage(final int channelIndex, final long[] missingObjectIds)
	{
		return "Store rejected: data references " + missingObjectIds.length
			+ " object id(s) with no existing entity (dangling references) in channel #" + channelIndex
			+ ": " + Arrays.toString(missingObjectIds)
			+ ". The referenced instances were trusted to already exist in the storage."
			+ " Re-store the referenced objects (e.g. with an eager storer) and retry."
		;
	}

}
