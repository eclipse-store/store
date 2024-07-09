package org.eclipse.store.afs.redis.types;

/*-
 * #%L
 * EclipseStore Abstract File System Redis
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

import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.configuration.types.Configuration;
import org.eclipse.serializer.configuration.types.ConfigurationBasedCreator;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;


public class RedisFileSystemCreator extends ConfigurationBasedCreator.Abstract<AFileSystem>
{
	public RedisFileSystemCreator()
	{
		super(AFileSystem.class);
	}

	@Override
	public AFileSystem create(
		final Configuration configuration
	)
	{
		final Configuration redisConfiguration = configuration.child("redis");
		if(redisConfiguration == null)
		{
			return null;
		}
		
		final String redisUri = redisConfiguration.get("uri");
		if(XChars.isEmpty(redisUri))
		{
			return null;
		}

		final boolean        cache     = redisConfiguration.optBoolean("cache").orElse(true);
		final RedisConnector connector = cache
			? RedisConnector.Caching(redisUri)
			: RedisConnector.New(redisUri)
		;
		return BlobStoreFileSystem.New(connector);
	}

}
