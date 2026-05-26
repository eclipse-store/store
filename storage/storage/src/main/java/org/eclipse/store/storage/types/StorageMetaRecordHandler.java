package org.eclipse.store.storage.types;

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

import java.nio.ByteBuffer;

/**
 * Load-time handler for one or more meta-record KIND codes. The kind-agnostic walker
 * ({@link StorageEntityInitializer}) hands every negative-length entry to a
 * {@link StorageMetaRecordRegistry}, which dispatches recognized entries to the handler registered for
 * their {@link StorageMetaRecord#kindOf(long) KIND}.
 * <p>
 * A handler may be registered under several KINDs (e.g. the chunk-checksum handler serves every
 * registered chunk-checksum algorithm); it re-reads {@code kindOf(address)} when it needs to know which
 * one it was invoked for. The {@code chunkStart} value is a running cursor the walker threads from one
 * meta record to the next: a handler that consumes a record advances it past that record's bytes; a
 * handler that does not move the boundary returns it unchanged.
 *
 * @see StorageMetaRecordRegistry
 */
public interface StorageMetaRecordHandler
{
	/**
	 * Handles a recognized meta record at {@code address} during the load walk.
	 *
	 * @param file       the data file currently being walked.
	 * @param buffer     the direct buffer holding the file's resident bytes (for in-place verification).
	 * @param address    absolute memory address of the meta record's start (offset 0 = LENGTH).
	 * @param chunkStart the running chunk-boundary cursor threaded by the walker.
	 * @return the updated {@code chunkStart} for the next meta record.
	 */
	public long onLoad(
		StorageLiveDataFile.Default file      ,
		ByteBuffer                  buffer    ,
		long                        address   ,
		long                        chunkStart
	);
}
