package org.eclipse.store.examples.gigamap.vector;

/*-
 * #%L
 * EclipseStore Example GigaMap
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

/**
 * Product with embedded vector.
 * <p>
 * Vectorization was done with all-minilm, name and category are included in the vector data
 */
public record Product(String name, Category category, double price, boolean inStock, float[] vector)
{
}
