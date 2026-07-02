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
 * <p>
 * Note that at the caller, this exception is wrapped: it appears as the cause of a
 * {@code PersistenceExceptionTransfer} / {@code StorageException} chain.
 */
public class StorageExceptionConsistencyDanglingReference extends StorageExceptionConsistency
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
		super(buildMessage(channelIndex, missingObjectIds));
		this.channelIndex     = channelIndex    ;
		this.missingObjectIds = missingObjectIds;
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
	 * @return the referenced object ids for which no entity exists.
	 */
	public long[] missingObjectIds()
	{
		return this.missingObjectIds;
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
