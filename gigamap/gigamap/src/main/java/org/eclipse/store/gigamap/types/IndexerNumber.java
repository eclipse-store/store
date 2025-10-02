package org.eclipse.store.gigamap.types;

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

/**
 * An interface providing indexing capabilities for entities using numerical keys.
 *
 * @param <E> the type of entities being indexed
 * @param <K> the numerical type of the key, which must extend {@link Number}
 */
public interface IndexerNumber<E, K extends Number> extends IndexerComparing<E, K>
{
    // typing interface
}
