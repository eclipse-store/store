package org.eclipse.store.afs.kafka.types;

/*-
 * #%L
 * EclipseStore Abstract File System Kafka
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

import java.util.Properties;

import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.serializer.configuration.types.Configuration;
import org.eclipse.serializer.configuration.types.ConfigurationBasedCreator;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;


public class KafkaFileSystemCreator extends ConfigurationBasedCreator.Abstract<AFileSystem>
{
	public KafkaFileSystemCreator()
	{
		super(AFileSystem.class, "kafka");
	}
	
	@Override
	public AFileSystem create(
		final Configuration configuration
	)
	{
		final Properties     kafkaProperties = new Properties();
		kafkaProperties.putAll(configuration.coalescedMap());
		final boolean        cache           = configuration.optBoolean("cache").orElse(true);
		final KafkaConnector connector       = cache
			? KafkaConnector.Caching(kafkaProperties)
			: KafkaConnector.New(kafkaProperties)
		;
		return BlobStoreFileSystem.New(connector);
	}
	
}
