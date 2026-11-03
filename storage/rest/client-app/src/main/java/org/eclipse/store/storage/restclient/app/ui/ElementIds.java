package org.eclipse.store.storage.restclient.app.ui;

/*-
 * #%L
 * EclipseStore Storage REST Client App
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

/**
 * @deprecated Deprecated since 4.3.0 and scheduled for removal in 5.0.0.
 * Use the Vanilla-JS storage viewer of {@code storage-restservice-javalin} instead, see
 * {@link org.eclipse.store.storage.restclient.app.types.RestClientAppAutoConfiguration}.
 */
@Deprecated(since = "4.3.0", forRemoval = true)
public interface ElementIds
{
	String TAB_STATISTICS    = "tab-statistics";
	String TAB_DATA          = "tab-data";
	String TEXT_DETAIL       = "text-detail";
	String GRID_DETAIL       = "grid-detail";
	String GRID_DATA         = "grid-data";
	String GRID_STATISTICS   = "grid-statistics";
	String BUTTON_CONNECT    = "button-connect";
	String COMBO_URL         = "combo-url";
	String BUTTON_DISCONNECT = "button-disconnect";
}
