/*-
 * #%L
 * EclipseStore Integrations SpringBoot Console
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
module integrations.spring.boot3.console {
    requires jakarta.annotation;
    requires org.slf4j;
    requires spring.beans;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires vaadin.spring;
    requires org.eclipse.store.storage.restclient.app;
    requires storage.restservice.springboot;
}
