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

/**
 * {@code @Vector(onDisk = true)} cannot be wired at compile time (it needs an index directory), so the
 * processor emits a note and generates no vector registration for this entity.
 */
public class OnDiskItem
{
	@Vector(dimension = 2, onDisk = true)
	private float[] embedding;

	public OnDiskItem()
	{
		super();
	}

	public OnDiskItem(final float[] embedding)
	{
		this.embedding = embedding;
	}

	public float[] getEmbedding()
	{
		return this.embedding;
	}
}
