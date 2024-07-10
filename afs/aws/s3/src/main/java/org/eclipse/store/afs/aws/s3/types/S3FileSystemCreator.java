package org.eclipse.store.afs.aws.s3.types;

import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.serializer.configuration.types.Configuration;

/*-
 * #%L
 * EclipseStore Abstract File System AWS S3
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

import org.eclipse.store.afs.aws.types.AwsFileSystemCreator;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

public class S3FileSystemCreator extends AwsFileSystemCreator
{
	public S3FileSystemCreator()
	{
		super("aws.s3");
	}
	
	@Override
	public AFileSystem create(final Configuration configuration)
	{
		final S3ClientBuilder clientBuilder = S3Client.builder();
		this.populateBuilder(clientBuilder, configuration);
		
		final S3Client    client    = clientBuilder.build();
		final boolean     cache     = configuration.optBoolean("cache").orElse(true);
		final boolean     directory = configuration.optBoolean("directory-bucket").orElse(false);
		final S3Connector connector =
			directory
				?	(cache
						? S3Connector.CachingDirectory(client)
						: S3Connector.NewDirectory(client)
					)
				:	(cache
						? S3Connector.Caching(client)
						: S3Connector.New(client)
					)
		;
		return BlobStoreFileSystem.New(connector);
	}
	
}
