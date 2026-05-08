
package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.lang.ref.Cleaner;

/**
 * Shared {@link Cleaner} for the gigamap module. Each {@link Cleaner#create()}
 * spawns its own daemon cleanup thread, so consolidating onto one instance
 * avoids one extra thread per consumer class.
 */
final class Cleaners
{
	static final Cleaner SHARED = Cleaner.create();

	private Cleaners()
	{
		throw new Error();
	}
}
