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
     * <code>true</code> if a directory bucket is used, <code>false</code> for general purpose buckets
     */
    private boolean directory = false;

	public boolean isDirectory()
	{
		return this.directory;
	}

	public void setDirectory(final boolean directory)
	{
		this.directory = directory;
	}
    
}
