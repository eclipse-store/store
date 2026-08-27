package org.eclipse.store.gigamap.codegen.test.base;

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

/**
 * Superclass in a <em>different package</em> than the entity that extends it, used to verify the
 * generated metamodel only reaches members it actually can: a {@code public} field is read directly,
 * a {@code private} field is read through its {@code public} getter.
 */
public class BaseEntity
{
	@Index
	public String region;

	@Index
	private String tag;

	protected BaseEntity()
	{
		super();
	}

	protected BaseEntity(final String region, final String tag)
	{
		this.region = region;
		this.tag    = tag;
	}

	public String getTag()
	{
		return this.tag;
	}
}
