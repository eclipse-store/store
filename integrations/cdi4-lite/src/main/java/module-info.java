/*-
 * #%L
 * Eclipse Store Integrations CDI 4 - lite
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */
module eclipse.store.integrations.cdi4.lite {
    requires java.logging;
    requires cache.api;
    requires jakarta.annotation;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires microprofile.config.api;
    requires org.eclipse.serializer.afs;
    requires org.eclipse.serializer.base;
    requires org.eclipse.serializer.configuration;
    requires org.eclipse.serializer.persistence;
    requires org.eclipse.serializer.persistence.binary;
    requires org.slf4j;
    requires org.eclipse.store.cache;
    requires org.eclipse.store.storage;
    requires org.eclipse.store.storage.embedded;
    requires org.eclipse.store.storage.embedded.configuration;

    exports org.eclipse.store.integrations.cdi;
}
