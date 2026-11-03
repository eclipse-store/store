package org.eclipse.store.storage.restclient.app.ui;

/*-
 * #%L
 * EclipseStore Storage REST Client App
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * @deprecated Deprecated since 4.3.0 and scheduled for removal in 5.0.0.
 * Use the Vanilla-JS storage viewer of {@code storage-restservice-javalin} instead, see
 * {@link org.eclipse.store.storage.restclient.app.types.RestClientAppAutoConfiguration}.
 */
@Push
@Theme(themeClass = Lumo.class, variant = Lumo.DARK)
@CssImport("./styles/shared-styles.css")
@Deprecated(since = "4.3.0", forRemoval = true)
public final class AppShell implements AppShellConfigurator
{

}
