package test.microstream.afs.blobstore;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - commons
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

import java.util.Map;

import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;

import test.microstream.afs.AbstractFileSystemTest;


public abstract class BlobStoreFileSystemTest extends AbstractFileSystemTest<BlobStoreFileSystem>
{
	@SuppressWarnings("unchecked")
	
	
	protected BlobStoreFileSystemTest()
	{
		super();
	}

	protected BlobStoreFileSystemTest(final String root)
	{
		super(root);
	}
	
	@Override
	protected void cleanup()
	{
		super.cleanup();
		
		this.fileSystem.ioHandler().connector().close();
	}
	
}
