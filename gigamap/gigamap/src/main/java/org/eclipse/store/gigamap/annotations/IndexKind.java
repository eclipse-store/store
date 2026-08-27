package org.eclipse.store.gigamap.annotations;

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

/**
 * Selects the bitmap index implementation that annotation-based index generation should produce for
 * a field. See {@link Index#kind()}.
 */
public enum IndexKind
{
	/**
	 * Let the generator pick a suitable index based on the field type (and {@link Index#binary()} /
	 * {@link Unique} for backward compatibility). This is the default.
	 */
	AUTO,

	/**
	 * Force a low-cardinality (hashing) bitmap index. Suitable for fields with few distinct values
	 * (status, category, enum, boolean). Supports exact-match and, for comparable types, range
	 * queries.
	 */
	LOW_CARDINALITY,

	/**
	 * Force a binary (long-keyed) index optimized for high cardinality / unique values. Supports
	 * only equality queries and disallows {@code null} keys. Available for natural number types,
	 * {@code String} and {@code UUID}.
	 */
	BINARY,

	/**
	 * Force a byte-decomposed (bit-sliced) index that supports range queries on high-cardinality
	 * numeric fields ({@code lessThan}, {@code greaterThan}, {@code between}). Available for all
	 * primitive numeric types and their wrappers.
	 */
	BIT_SLICED
}
