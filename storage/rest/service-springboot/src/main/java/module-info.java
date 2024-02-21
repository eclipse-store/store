/*-
 * #%L
 * EclipseStore Storage REST Service SpringBoot
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
module storage.restservice.springboot {
    requires com.fasterxml.jackson.annotation;
    requires jakarta.annotation;
    requires org.slf4j;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.web;
    requires integrations.spring.boot3;
    requires transitive storage;
    requires transitive storage.embedded;
    requires transitive storage.restadapter;

    exports org.eclipse.store.storage.restservice.spring.boot.types;
}
