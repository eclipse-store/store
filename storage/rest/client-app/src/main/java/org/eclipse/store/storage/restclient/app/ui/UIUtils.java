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

import com.vaadin.flow.component.orderedlayout.ThemableLayout;

final class UIUtils
{
	public static <L extends ThemableLayout> L compact(
		final L layout
	)
	{
		layout.setPadding(false);
		layout.setMargin(false);
		layout.setSpacing(true);
		return layout;
	}

	public static String imagePath(String image)
	{
		return "/frontend/images/" + image;
	}
	
	
	private UIUtils()
	{
		throw new Error();
	}
}
