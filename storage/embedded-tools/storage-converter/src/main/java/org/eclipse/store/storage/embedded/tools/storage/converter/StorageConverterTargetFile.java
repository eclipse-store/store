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

	public void writeBytes(final ByteBuffer buffer)
	{
		this.size += this.file.writeBytes(buffer);
	}

	public long fileNumber()
	{
		return this.fileNumber;
	}

	public long size()
	{
		return this.size;
	}

	public void release()
	{
		this.file.release();
	}

}
