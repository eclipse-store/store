package org.eclipse.store.gigamap.exceptions;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.types.GigaIndex;


/**
 * Marker interface for exceptions that signal a GigaMap index has become <em>stale</em> relative to
 * the current state of its entities: the persisted index keys no longer match the keys re-derived
 * from the loaded entities. The typical causes are a direct (untracked) mutation of an indexed field
 * or entity class evolution (an indexed field renamed or retyped across releases without a
 * value-preserving refactoring mapping).
 * <p>
 * The distinguishing property is that the <em>entity data itself is valid</em> — only the derived
 * index is out of date. Consequently, a mutating operation that encounters this condition must not
 * treat the entity as corrupt and destroy it; it retains the committed entity and reports this
 * exception so the caller can rebuild the indices via {@link org.eclipse.store.gigamap.types.GigaMap#reindex()}.
 *
 * @see org.eclipse.store.gigamap.types.GigaMap#reindex()
 */
public interface StaleIndexException
{
	/**
	 * The index that was detected to be stale.
	 *
	 * @return the associated {@link GigaIndex} instance
	 */
	public GigaIndex<?> index();
}
