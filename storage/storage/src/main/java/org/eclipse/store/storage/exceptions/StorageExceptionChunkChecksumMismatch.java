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
 * Raised when a chunk-checksum meta record's stored hash does not match the checksum recomputed over the
 * chunk's preceding bytes during load-time verification. Indicates either bit-rot on the underlying
 * storage or a writer-side bug.
 */
public class StorageExceptionChunkChecksumMismatch extends StorageExceptionConsistency
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final long   fileNumber   ;
	private final long   chunkPosition;
	private final byte[] expectedHash ;
	private final byte[] actualHash   ;



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public StorageExceptionChunkChecksumMismatch(
		final long   fileNumber   ,
		final long   chunkPosition,
		final byte[] expectedHash ,
		final byte[] actualHash
	)
	{
		super(buildMessage(fileNumber, chunkPosition, expectedHash, actualHash));
		this.fileNumber    = fileNumber   ;
		this.chunkPosition = chunkPosition;
		this.expectedHash  = expectedHash ;
		this.actualHash    = actualHash   ;
	}



	///////////////////////////////////////////////////////////////////////////
	// getters //
	////////////

	public long fileNumber()
	{
		return this.fileNumber;
	}

	public long chunkPosition()
	{
		return this.chunkPosition;
	}

	public byte[] expectedHash()
	{
		return this.expectedHash;
	}

	public byte[] actualHash()
	{
		return this.actualHash;
	}



	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////

	private static String buildMessage(
		final long   fileNumber   ,
		final long   chunkPosition,
		final byte[] expectedHash ,
		final byte[] actualHash
	)
	{
		return "ChunkChecksumV1 mismatch in data file #" + fileNumber
			+ " at position " + chunkPosition
			+ ": expected hash " + toHex(expectedHash)
			+ ", actual hash "   + toHex(actualHash);
	}

	private static String toHex(final byte[] bytes)
	{
		if(bytes == null)
		{
			return "(null)";
		}
		final StringBuilder sb = new StringBuilder(bytes.length * 2);
		for(final byte b : bytes)
		{
			sb.append(Character.forDigit((b >> 4) & 0xF, 16))
			  .append(Character.forDigit( b       & 0xF, 16));
		}
		return sb.toString();
	}
}
