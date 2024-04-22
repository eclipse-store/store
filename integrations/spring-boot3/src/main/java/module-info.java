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
open module org.eclipse.store.integrations.spring.boot
{
    exports org.eclipse.store.integrations.spring.boot.types.configuration;
    exports org.eclipse.store.integrations.spring.boot.types.configuration.aws;
    exports org.eclipse.store.integrations.spring.boot.types.configuration.azure;
    exports org.eclipse.store.integrations.spring.boot.types.configuration.googlecloud;
    exports org.eclipse.store.integrations.spring.boot.types.configuration.googlecloud.firestore;
    exports org.eclipse.store.integrations.spring.boot.types.configuration.oraclecloud;
    exports org.eclipse.store.integrations.spring.boot.types.configuration.redis;
    exports org.eclipse.store.integrations.spring.boot.types.configuration.sql;
    exports org.eclipse.store.integrations.spring.boot.types;
    exports org.eclipse.store.integrations.spring.boot.types.converter;
    exports org.eclipse.store.integrations.spring.boot.types.factories;
    exports org.eclipse.store.integrations.spring.boot.types.concurrent;
    exports org.eclipse.store.integrations.spring.boot.types.suppliers;

    requires transitive spring.beans;
    requires transitive spring.boot;
    requires transitive spring.boot.autoconfigure;
    requires transitive spring.context;
    requires transitive spring.core;
    requires transitive org.eclipse.store.storage.embedded.configuration;
    requires transitive org.eclipse.serializer.configuration;
    requires transitive org.eclipse.serializer.persistence.binary.jdk17;
    requires transitive org.eclipse.serializer.persistence.binary.jdk8;
    requires org.aspectj.weaver;
}
