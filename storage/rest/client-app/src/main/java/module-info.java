/*-
 * #%L
 * EclipseStore Storage REST Client App
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

module org.eclipse.store.storage.restclient.app
{
	exports org.eclipse.store.storage.restclient.app.types;
	exports org.eclipse.store.storage.restclient.app.ui;
	
	provides com.vaadin.flow.server.VaadinServiceInitListener
	    with org.eclipse.store.storage.restclient.app.types.ApplicationServiceInitListener
	;
	
	requires transitive flow.data;
	requires transitive flow.html.components;
	requires transitive flow.server;
	requires transitive gwt.elemental;
	requires transitive org.eclipse.serializer.base;
	requires transitive org.eclipse.store.storage.restadapter;
	requires transitive org.eclipse.store.storage.restclient;
	requires transitive org.eclipse.store.storage.restclient.jersey;
	requires transitive org.apache.tomcat.embed.core;
	requires transitive org.slf4j;
	requires transitive spring.beans;
	requires transitive spring.boot;
	requires transitive spring.boot.autoconfigure;
	requires transitive spring.context;
	requires transitive spring.core;
	requires transitive spring.web;
	requires transitive vaadin.spring;
	requires transitive vaadin.button.flow;
	requires transitive vaadin.combo.box.flow;
	requires transitive vaadin.details.flow;
	requires transitive vaadin.grid.flow;
	requires transitive vaadin.lumo.theme;
	requires transitive vaadin.notification.flow;
	requires transitive vaadin.ordered.layout.flow;
	requires transitive vaadin.split.layout.flow;
	requires transitive vaadin.tabs.flow;
	requires transitive vaadin.text.field.flow;
}
