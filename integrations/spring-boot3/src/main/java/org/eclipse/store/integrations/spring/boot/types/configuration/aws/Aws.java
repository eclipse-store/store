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

import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Aws
{

    @NestedConfigurationProperty
    private Dynamodb dynamodb;

    @NestedConfigurationProperty
    private S3 s3;

    public Dynamodb getDynamodb()
    {
        return this.dynamodb;
    }

    public void setDynamodb(final Dynamodb dynamodb)
    {
        this.dynamodb = dynamodb;
    }

    public S3 getS3()
    {
        return this.s3;
    }

    public void setS3(final S3 s3)
    {
        this.s3 = s3;
    }
}
