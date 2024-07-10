package org.eclipse.store.afs.oraclecloud.objectstorage.types;

/*-
 * #%L
 * EclipseStore Abstract File System Oracle Cloud ObjectStorage
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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.configuration.exceptions.ConfigurationException;
import org.eclipse.serializer.configuration.types.Configuration;
import org.eclipse.serializer.configuration.types.ConfigurationBasedCreator;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.ClientConfiguration.ClientConfigurationBuilder;
import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;

public class OracleCloudObjectStorageFileSystemCreator extends ConfigurationBasedCreator.Abstract<AFileSystem>
{
	private final static String CLASSPATH_PREFIX = "classpath:";

	public OracleCloudObjectStorageFileSystemCreator()
	{
		super(AFileSystem.class, "oraclecloud.object-storage");
	}

	@Override
	public AFileSystem create(final Configuration configuration)
	{
		String              filePath                = null;
		String              profile                 = null;
		Charset             charset                 = StandardCharsets.UTF_8;
		final Configuration configFileConfiguration = configuration.child("config-file");
		if(configFileConfiguration != null)
		{
			filePath = configFileConfiguration.get("path");
			profile  = configFileConfiguration.get("profile");

			final String charsetName = configFileConfiguration.get("charset");
			if(charsetName != null)
			{
				charset = Charset.forName(charsetName);
			}
		}

		try
		{
			final ConfigFileReader.ConfigFile configFile = XChars.isEmpty(filePath)
				? ConfigFileReader.parseDefault(profile)
				: ConfigFileReader.parse(
					this.configFileInputStream(configFileConfiguration, filePath),
					profile,
					charset
				)
			;
			final AuthenticationDetailsProvider authDetailsProvider =
				new ConfigFileAuthenticationDetailsProvider(configFile)
			;

			final ClientConfigurationBuilder clientConfigurationBuilder = ClientConfiguration.builder();
			final Configuration              clientConfiguration        = configuration.child("client");
			if(clientConfiguration != null)
			{
				this.createClientConfiguration(
					clientConfigurationBuilder,
					clientConfiguration
				);
			}

			final ObjectStorageClient client = new ObjectStorageClient(
				authDetailsProvider,
				clientConfigurationBuilder.build()
			);
			configuration.opt("region").ifPresent(
				value -> client.setRegion(value)
			);
			configuration.opt("endpoint").ifPresent(
				value -> client.setEndpoint(value)
			);

			final boolean        cache           = configuration.optBoolean("cache").orElse(true);
			final OracleCloudObjectStorageConnector connector       = cache
				? OracleCloudObjectStorageConnector.Caching(client)
				: OracleCloudObjectStorageConnector.New(client)
			;
			return BlobStoreFileSystem.New(connector);
		}
		catch(final IOException e)
		{
			throw new ConfigurationException(configuration, e);
		}
	}

	private InputStream configFileInputStream(
		final Configuration configuration,
		final String        path
	)
	throws IOException
	{
		if(path.toLowerCase().startsWith(CLASSPATH_PREFIX))
		{
			return this.getClass().getResourceAsStream(path.substring(CLASSPATH_PREFIX.length()));
		}

		try
		{
			final URL url = new URL(path);
			return url.openStream();
		}
		catch(final MalformedURLException e)
		{
			return new FileInputStream(path);
		}
	}

	private void createClientConfiguration(
		final ClientConfigurationBuilder builder      ,
		final Configuration              configuration
	)
	{
		configuration.optInteger("connection-timeout-millis").ifPresent(
			value -> builder.connectionTimeoutMillis(value)
		);

		configuration.optInteger("read-timeout-millis").ifPresent(
			value -> builder.readTimeoutMillis(value)
		);

		configuration.optInteger("max-async-threads").ifPresent(
			value -> builder.maxAsyncThreads(value)
		);
	}

}
