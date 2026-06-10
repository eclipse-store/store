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
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerString;

/**
 * The creator is a {@code private} nested class: reachable for the annotation here, but not from the
 * generated sibling metamodel. The processor must emit a note and skip it (runtime fallback), still
 * producing a compilable {@code PrivateCreatorBean_}.
 */
public class PrivateCreatorBean
{
	@Index(creator = SecretCreator.class)
	private String secret;

	public PrivateCreatorBean()
	{
		super();
	}

	public PrivateCreatorBean(final String secret)
	{
		this.secret = secret;
	}

	public String getSecret()
	{
		return this.secret;
	}

	private static final class SecretCreator implements Indexer.Creator<PrivateCreatorBean, String>
	{
		@Override
		public Indexer<PrivateCreatorBean, String> create()
		{
			return new IndexerString.Abstract<>()
			{
				@Override
				public String name()
				{
					return "secret";
				}

				@Override
				protected String getString(final PrivateCreatorBean entity)
				{
					return entity.getSecret();
				}
			};
		}
	}
}
