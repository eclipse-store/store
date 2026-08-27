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
 * Raised when a chunk-checksum meta record's stored chunk-start offset does not match the chunk boundary the
 * load walk reconstructed by following entity LENGTH prefixes. Indicates a framing desync &mdash; a corrupted
 * entity LENGTH rerouted the walker around a covering record &mdash; rather than bit-rot of a chunk's content
 * (which surfaces as {@link StorageExceptionChunkChecksumMismatch}).
 */
public class StorageExceptionChunkBoundaryMismatch extends StorageExceptionConsistency
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final long fileNumber         ;
	private final long recordPosition     ;
	private final long expectedChunkStart ;
	private final long actualChunkStart   ;



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public StorageExceptionChunkBoundaryMismatch(
		final long fileNumber         ,
		final long recordPosition     ,
		final long expectedChunkStart ,
		final long actualChunkStart
	)
	{
		super(buildMessage(fileNumber, recordPosition, expectedChunkStart, actualChunkStart));
		this.fileNumber         = fileNumber        ;
		this.recordPosition     = recordPosition    ;
		this.expectedChunkStart = expectedChunkStart;
		this.actualChunkStart   = actualChunkStart  ;
	}



	///////////////////////////////////////////////////////////////////////////
	// getters //
	////////////

	public long fileNumber()
	{
		return this.fileNumber;
	}

	public long recordPosition()
	{
		return this.recordPosition;
	}

	public long expectedChunkStart()
	{
		return this.expectedChunkStart;
	}

	public long actualChunkStart()
	{
		return this.actualChunkStart;
	}



	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////

	private static String buildMessage(
		final long fileNumber         ,
		final long recordPosition     ,
		final long expectedChunkStart ,
		final long actualChunkStart
	)
	{
		return "Chunk-boundary mismatch in data file #" + fileNumber
			+ " at record position " + recordPosition
			+ ": record covers chunk starting at " + expectedChunkStart
			+ ", but the load walk reconstructed a chunk start of " + actualChunkStart
			+ " (framing desync — a corrupted entity length rerouted the walk past a covering record)."
		;
	}
}
