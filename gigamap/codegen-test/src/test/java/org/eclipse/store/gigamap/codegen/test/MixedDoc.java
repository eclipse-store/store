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

import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.store.gigamap.jvector.annotations.Vector;
import org.eclipse.store.gigamap.lucene.annotations.FullText;

/**
 * Exercises all three families wired into a single generated metamodel: a bitmap index, a Lucene
 * full-text field, and a JVector embedding.
 */
public class MixedDoc
{
	@Index
	private String category;

	@FullText
	private String body;

	@Vector(dimension = 3)
	private float[] embedding;

	public MixedDoc()
	{
		super();
	}

	public MixedDoc(final String category, final String body, final float[] embedding)
	{
		this.category  = category;
		this.body      = body;
		this.embedding = embedding;
	}

	public String getCategory()
	{
		return this.category;
	}

	public String getBody()
	{
		return this.body;
	}

	public float[] getEmbedding()
	{
		return this.embedding;
	}
}
