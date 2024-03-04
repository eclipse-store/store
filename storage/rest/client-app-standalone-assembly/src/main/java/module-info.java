/*-
 * #%L
 * EclipseStore Storage REST Client App Standalone Assembly
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
module storage.restclient.app.standalone.assembly {
    opens org.eclipse.store.storage.restclient.app.standalone.types to spring.core, spring.beans, spring.context;

    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires org.eclipse.store.storage.restclient.app;

}
