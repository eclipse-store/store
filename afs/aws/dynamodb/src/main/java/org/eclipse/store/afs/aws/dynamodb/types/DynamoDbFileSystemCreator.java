package org.eclipse.store.afs.aws.dynamodb.types;

/*-
 * #%L
 * EclipseStore Abstract File System AWS DynamoDB
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
import org.eclipse.serializer.configuration.types.Configuration;
import org.eclipse.store.afs.aws.types.AwsFileSystemCreator;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

public class DynamoDbFileSystemCreator extends AwsFileSystemCreator
{
	public DynamoDbFileSystemCreator()
	{
		super();
	}
	
	@Override
	public AFileSystem create(
		final Configuration configuration
	)
	{
		final Configuration dynamoConfiguration = configuration.child("aws.dynamodb");
		if(dynamoConfiguration == null)
		{
			return null;
		}
		
		final DynamoDbClientBuilder clientBuilder = DynamoDbClient.builder();
		this.populateBuilder(clientBuilder, dynamoConfiguration);
		
		final DynamoDbClient    client    = clientBuilder.build();
		final boolean           cache     = dynamoConfiguration.optBoolean("cache").orElse(true);
		final DynamoDbConnector connector = cache
			? DynamoDbConnector.Caching(client)
			: DynamoDbConnector.New(client)
		;
		return BlobStoreFileSystem.New(connector);
	}
	
}
