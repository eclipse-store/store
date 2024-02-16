/*-
 * #%L
 * Eclipse Store Wildfly CDI Example
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
module eclipse.store.examples.wildfly {
    requires java.logging;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.json.bind;
    requires jakarta.ws.rs;
    requires microprofile.openapi.api;
    requires org.eclipse.serializer.persistence;
    requires eclipse.store.integrations.cdi4.lite;
    requires jdk.unsupported;
}
