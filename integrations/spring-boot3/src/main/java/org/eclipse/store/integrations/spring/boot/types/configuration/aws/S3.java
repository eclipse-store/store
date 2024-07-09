package org.eclipse.store.integrations.spring.boot.types.configuration.aws;

/*-
 * #%L
 * microstream-integrations-spring-boot3
 * %%
 * Copyright (C) 2019 - 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

public class S3 extends AbstractAwsProperties
{
    /**
     * <code>true</code> if a directory bucket (e.g. Express One Zone) is used,
     * <code>false</code> for general purpose buckets (e.g. Standard)
     * 
     * @see https://aws.amazon.com/s3/storage-classes/
     * @see https://docs.aws.amazon.com/AmazonS3/latest/userguide/directory-buckets-overview.html
     */
    private boolean directoryBucket = false;

	public boolean isDirectoryBucket()
	{
		return this.directoryBucket;
	}

	public void setDirectoryBucket(final boolean directoryBucket)
	{
		this.directoryBucket = directoryBucket;
	}
    
}
