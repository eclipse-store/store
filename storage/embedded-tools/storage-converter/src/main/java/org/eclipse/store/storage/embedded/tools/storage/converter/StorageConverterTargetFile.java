package org.eclipse.store.storage.embedded.tools.storage.converter;

/*-
 * #%L
 * EclipseStore Storage Embedded Tools Storage Converter
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

import java.nio.ByteBuffer;

import org.eclipse.serializer.afs.types.AWritableFile;

/**
 * Helper to hold all information of a single target "File"
 * of a {@link StorageConverterTarget} a on place
 */
public class StorageConverterTargetFile
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private final AWritableFile file;
	private final long          fileNumber;
	private       long          size;

	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	/**
	 * Create a {@link StorageConverterTargetFile} instance
	 * 
	 * @param file the target storage file
	 * @param fileNumber the file number
	 */
	public StorageConverterTargetFile(final AWritableFile file, final long fileNumber)
	{
		super();
		this.file = file;
		this.fileNumber = fileNumber;
	}

	/**
	 * Appends the remaining bytes of {@code buffer} to the underlying file and grows the tracked size
	 * accordingly.
	 *
	 * @param buffer the byte buffer to write; the bytes between its current position and limit are written.
	 */
	public void writeBytes(final ByteBuffer buffer)
	{
		this.size += this.file.writeBytes(buffer);
	}

	/**
	 * Returns the storage data file number this target file represents.
	 *
	 * @return the file number.
	 */
	public long fileNumber()
	{
		return this.fileNumber;
	}

	/**
	 * Returns the number of bytes written to this file so far.
	 *
	 * @return the cumulative number of bytes written.
	 */
	public long size()
	{
		return this.size;
	}

	/**
	 * Releases the underlying writable file handle. Must be called once the file has been completed.
	 */
	public void release()
	{
		this.file.release();
	}

}
