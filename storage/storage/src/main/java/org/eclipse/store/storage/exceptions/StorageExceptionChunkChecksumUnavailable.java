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
 * Raised by off-line repair tooling (e.g. {@code StorageObjectRestorer}) when the target store is
 * chunk-checksum protected on disk but the tool cannot produce a compliant covered file &mdash; either
 * because it was created without a {@code StorageConfiguration} (no chunk-checksum provider, hence no
 * write algorithm), or because the configured provider's primary kind does not match the store's on-disk
 * kind (writing a mismatched kind would manufacture a mixed store).
 * <p>
 * This turns an otherwise silent loss of integrity coverage into an actionable failure: reconstruct the
 * tool with a configuration whose {@code chunkChecksumProvider} matches the store.
 */
public class StorageExceptionChunkChecksumUnavailable extends StorageException
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final long onDiskKind    ;
	private final long configuredKind; // -1 when no provider was available at all



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public StorageExceptionChunkChecksumUnavailable(final long onDiskKind, final long configuredKind)
	{
		super(buildMessage(onDiskKind, configuredKind));
		this.onDiskKind     = onDiskKind    ;
		this.configuredKind = configuredKind;
	}



	///////////////////////////////////////////////////////////////////////////
	// getters //
	////////////

	public long onDiskKind()
	{
		return this.onDiskKind;
	}

	/**
	 * @return the configured provider's primary kind, or {@code -1} if no provider was available.
	 */
	public long configuredKind()
	{
		return this.configuredKind;
	}



	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////

	private static String buildMessage(final long onDiskKind, final long configuredKind)
	{
		if(configuredKind < 0L)
		{
			return "Store uses chunk-checksum kind 0x" + Long.toHexString(onDiskKind)
				+ " but this restorer was created without a StorageConfiguration; reconstruct it with a"
				+ " configuration whose chunkChecksumProvider matches the store.";
		}
		return "Store uses chunk-checksum kind 0x" + Long.toHexString(onDiskKind)
			+ " but the configured provider writes kind 0x" + Long.toHexString(configuredKind)
			+ "; configure a matching StorageChunkChecksumProvider.";
	}
}
