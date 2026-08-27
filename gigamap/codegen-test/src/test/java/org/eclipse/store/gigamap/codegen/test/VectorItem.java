package org.eclipse.store.gigamap.codegen.test;

/*-
 * #%L
 * EclipseStore GigaMap Codegen
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

import org.eclipse.store.gigamap.jvector.annotations.Vector;
import org.eclipse.store.gigamap.jvector.VectorSimilarityFunction;

/**
 * JVector test entity with a private {@code float[]} embedding read through its getter.
 */
public class VectorItem
{
	@Vector(dimension = 4, similarity = VectorSimilarityFunction.COSINE)
	private float[] embedding;

	public VectorItem()
	{
		super();
	}

	public VectorItem(final float[] embedding)
	{
		this.embedding = embedding;
	}

	public float[] getEmbedding()
	{
		return this.embedding;
	}
}
