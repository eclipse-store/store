/*-
 * #%L
 * Eclipse Store Storage REST Client App
 * %%
 * Copyright (C) 2023 Eclipse Foundation
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
	
	requires flow.data;
	requires flow.html.components;
	requires flow.server;
	requires gwt.elemental;
	requires org.eclipse.store.base;
	requires org.eclipse.store.storage.restadapter;
	requires org.eclipse.store.storage.restclient;
	requires org.eclipse.store.storage.restclient.jersey;
	requires org.apache.tomcat.embed.core;
	requires org.slf4j;
	requires spring.beans;
	requires spring.boot;
	requires spring.boot.autoconfigure;
	requires spring.context;
	requires spring.core;
	requires spring.web;
	requires vaadin.button.flow;
	requires vaadin.combo.box.flow;
	requires vaadin.details.flow;
	requires vaadin.grid.flow;
	requires vaadin.lumo.theme;
	requires vaadin.notification.flow;
	requires vaadin.ordered.layout.flow;
	requires vaadin.split.layout.flow;
	requires vaadin.tabs.flow;
	requires vaadin.text.field.flow;
}
