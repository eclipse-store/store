package org.eclipse.store.gigamap.indexer.annotation.custom;

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

import org.eclipse.store.gigamap.annotations.Index;


public class CustomTypeEntity
{
	@Index
	public CustomType customType;
	
	public CustomTypeEntity(final CustomType customType)
	{
		this.customType = customType;
	}
	
	public CustomType customType()
	{
		return this.customType;
	}
	
}
