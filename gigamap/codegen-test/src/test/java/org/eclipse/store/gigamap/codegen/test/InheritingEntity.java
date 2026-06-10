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
import org.eclipse.store.gigamap.codegen.test.base.BaseEntity;

/**
 * Entity inheriting index-annotated members from a different-package {@link BaseEntity}, plus its own.
 */
public class InheritingEntity extends BaseEntity
{
	@Index
	private String name;

	public InheritingEntity()
	{
		super();
	}

	public InheritingEntity(final String region, final String tag, final String name)
	{
		super(region, tag);
		this.name = name;
	}

	public String getName()
	{
		return this.name;
	}
}
