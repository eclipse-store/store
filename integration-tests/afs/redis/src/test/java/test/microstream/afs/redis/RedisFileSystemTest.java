package test.microstream.afs.redis;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - redis
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

import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.eclipse.store.afs.redis.types.RedisConnector;

import test.microstream.afs.blobstore.BlobStoreFileSystemTest;

public class RedisFileSystemTest extends BlobStoreFileSystemTest
{
	@Override
	protected BlobStoreFileSystem setupFileSystem()
	{
		return BlobStoreFileSystem.New(
			RedisConnector.New(Container.redisUrl())
		);
	}
	
	@Override
	protected void cleanupFileSystem()
	{
	}

}
