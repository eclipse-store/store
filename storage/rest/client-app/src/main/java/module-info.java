/*-
 * #%L
 * EclipseStore Storage REST Client App
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
module org.eclipse.store.storage.restclient.app {
	exports org.eclipse.store.storage.restclient.app.types;
	exports org.eclipse.store.storage.restclient.app.ui;

	provides com.vaadin.flow.server.VaadinServiceInitListener
		with org.eclipse.store.storage.restclient.app.types.ApplicationServiceInitListener;

	opens org.eclipse.store.storage.restclient.app.types to spring.core, spring.beans, spring.context;

	requires flow.data;
	requires flow.html.components;
	requires flow.server;
	requires gwt.elemental;
	requires org.apache.tomcat.embed.core;
	requires org.eclipse.serializer.base;
	requires org.eclipse.store.storage.restadapter;
	requires org.eclipse.store.storage.restclient;
	requires org.eclipse.store.storage.restclient.jersey;
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
	requires vaadin.flow.components.base;
	requires vaadin.grid.flow;
	requires vaadin.lumo.theme;
	requires vaadin.notification.flow;
	requires vaadin.ordered.layout.flow;
	requires vaadin.renderer.flow;
	requires vaadin.split.layout.flow;
	requires vaadin.spring;
	requires vaadin.tabs.flow;
	requires vaadin.text.field.flow;
}
