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

import java.util.regex.Pattern;

import org.eclipse.store.afs.blobstore.types.BlobStorePath;

public interface KafkaPathValidator extends BlobStorePath.Validator
{

	public static KafkaPathValidator New()
	{
		return new KafkaPathValidator.Default();
	}


	public static class Default implements KafkaPathValidator
	{
		Default()
		{
			super();
		}

		/*
		 * https://stackoverflow.com/questions/37062904/what-are-apache-kafka-topic-name-limitations
		 */
		@Override
		public void validate(
			final BlobStorePath path
		)
		{
			final String name = path.fullQualifiedName().replace(BlobStorePath.SEPARATOR_CHAR, '_');
			if(name.length() > 249)
			{
				throw new IllegalArgumentException(
					"full qualified path name cannot be longer than 249 characters"
				);
			}
			if(!Pattern.matches(
				"[a-zA-Z0-9\\._\\-]*",
				name
			))
			{
				throw new IllegalArgumentException(
					"path can contain only letters, numbers, periods (.), underscores (_) and dashes (-)"
				);
			}
		}

	}

}
