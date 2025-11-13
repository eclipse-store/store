package org.eclipse.store.gigamap;

/*-
 * #%L
 * EclipseStore GigaMap
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

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class HookExtension implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

	@Override
	public void beforeAll(ExtensionContext context) {
		System.out.println("beforeAll hook call");
	}

	@Override
	public void close() {
		// Your "after all tests" logic goes here
	}

}
