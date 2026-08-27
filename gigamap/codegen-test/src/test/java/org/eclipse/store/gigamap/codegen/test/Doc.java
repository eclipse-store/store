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

import org.eclipse.store.gigamap.lucene.annotations.FullText;

/**
 * Lucene full-text test entity: a tokenized field and an exact-match keyword field, both private and
 * read through their getters.
 */
public class Doc
{
	@FullText
	private String title;

	@FullText(analyzed = false)
	private String category;

	public Doc()
	{
		super();
	}

	public Doc(final String title, final String category)
	{
		this.title    = title;
		this.category = category;
	}

	public String getTitle()
	{
		return this.title;
	}

	public String getCategory()
	{
		return this.category;
	}
}
