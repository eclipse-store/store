package org.eclipse.store.afs.azure.storage.types;

/*-
 * #%L
 * EclipseStore Abstract File System Azure Storage
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
import org.eclipse.serializer.configuration.types.ConfigurationBasedCreator;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;

import com.azure.core.credential.BasicAuthenticationCredential;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;


public class AzureStorageFileSystemCreator extends ConfigurationBasedCreator.Abstract<AFileSystem>
{
	public AzureStorageFileSystemCreator()
	{
		super(AFileSystem.class, "azure.storage");
	}

	@Override
	public AFileSystem create(
		final Configuration configuration
	)
	{
		final BlobServiceClientBuilder clientBuilder = new BlobServiceClientBuilder();

		configuration.opt("endpoint").ifPresent(
			value -> clientBuilder.endpoint(value)
		);

		configuration.opt("connection-string").ifPresent(
			value -> clientBuilder.connectionString(value)
		);

		configuration.opt("encryption-scope").ifPresent(
			value -> clientBuilder.encryptionScope(value)
		);

		configuration.opt("credentials.type").ifPresent(credentialsType ->
		{
			switch(credentialsType)
			{
				case "basic":
				{
					clientBuilder.credential(
						new BasicAuthenticationCredential(
							configuration.get("credentials.username"),
							configuration.get("credentials.password")
						)
					);
				}
				break;

				case "shared-key":
				{
					clientBuilder.credential(
						new StorageSharedKeyCredential(
							configuration.get("credentials.account-name"),
							configuration.get("credentials.account-key")
						)
					);
				}
				break;

				default:
					// no credentials provider is used if not explicitly set
			}
		});

		final Configuration furtherConfiguration = configuration.child("configuration");
		if(furtherConfiguration != null)
		{
			final com.azure.core.util.Configuration config = new com.azure.core.util.Configuration();
			for(final String key : furtherConfiguration.keys())
			{
				final String value = furtherConfiguration.get(key);
				config.put(key, value);
			}
			clientBuilder.configuration(config);
		}

		final BlobServiceClient     client    = clientBuilder.buildClient();
		final boolean               cache     = configuration.optBoolean("cache").orElse(true);
		final AzureStorageConnector connector = cache
			? AzureStorageConnector.Caching(client)
			: AzureStorageConnector.New(client)
		;
		return BlobStoreFileSystem.New(connector);
	}

}
