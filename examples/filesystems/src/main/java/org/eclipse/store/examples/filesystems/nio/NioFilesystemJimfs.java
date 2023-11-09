
package org.eclipse.store.examples.filesystems.nio;

/*-
 * #%L
 * EclipseStore Example File Systems
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

import java.nio.file.FileSystem;

import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageLiveFileProvider;

import com.google.common.jimfs.Jimfs;


public class NioFilesystemJimfs
{
	@SuppressWarnings("unused")
	public static void main(
		final String[] args
	)
	{
		// create jimfs filesystem
		final FileSystem             jimfs   = Jimfs.newFileSystem();
		
		// start storage with jimfs path
		final EmbeddedStorageManager storage = EmbeddedStorage.start(jimfs.getPath("storage"));
		storage.shutdown();
		
		// or create file provider with jimsfs filesytem for further configuration
		final NioFileSystem           fileSystem   = NioFileSystem.New(jimfs);
		final StorageLiveFileProvider fileProvider = StorageLiveFileProvider.Builder(fileSystem)
			.setDirectory(fileSystem.ensureDirectoryPath("storage"))
			.createFileProvider()
		;
	}
}
