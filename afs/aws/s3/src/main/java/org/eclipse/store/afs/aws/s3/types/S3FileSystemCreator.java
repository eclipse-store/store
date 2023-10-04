package org.eclipse.store.afs.aws.s3.types;

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
import org.eclipse.store.configuration.types.Configuration;
import org.eclipse.serializer.afs.types.AFileSystem;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

public class S3FileSystemCreator extends AwsFileSystemCreator
{
	public S3FileSystemCreator()
	{
		super();
	}
	
	@Override
	public AFileSystem create(
		final Configuration configuration
	)
	{
		final Configuration s3Configuration = configuration.child("aws.s3");
		if(s3Configuration == null)
		{
			return null;
		}
		
		final S3ClientBuilder clientBuilder = S3Client.builder();
		this.populateBuilder(clientBuilder, s3Configuration);
		
		final S3Client    client    = clientBuilder.build();
		final boolean     cache     = configuration.optBoolean("cache").orElse(true);
		final S3Connector connector = cache
			? S3Connector.Caching(client)
			: S3Connector.New(client)
		;
		return BlobStoreFileSystem.New(connector);
	}
	
}
