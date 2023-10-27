package org.eclipse.store.integrations.spring.boot.types.configuration.googlecloud;

/*-
 * #%L
 * spring-boot3
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

import org.eclipse.store.integrations.spring.boot.types.configuration.googlecloud.firestore.Firestore;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Googlecloud
{
    @NestedConfigurationProperty
    private Firestore firestore;

    public Firestore getFirestore()
    {
        return firestore;
    }

    public void setFirestore(Firestore firestore)
    {
        this.firestore = firestore;
    }
}
