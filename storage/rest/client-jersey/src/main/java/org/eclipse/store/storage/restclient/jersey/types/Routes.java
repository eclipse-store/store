package org.eclipse.store.storage.restclient.jersey.types;

/*-
 * #%L
 * EclipseStore Storage REST Client Jersey
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

public interface Routes
{
	public String dictionary();
	
	public String root();
	
	public String object();
	
	public String filesStatistics();
	
	
	public static Routes Default()
	{
		return new Default(
			"dictionary",
			"root",
			"object",
			"maintenance/filesStatistics"
		);
	}	
	
	public static Routes New(
		final String dictionary, 
		final String root, 
		final String object, 
		final String filesStatistics
	)
	{
		return new Default(
			dictionary, 
			root, 
			object, 
			filesStatistics
		);	
	}
	
	
	public static class Default implements Routes
	{
		private final String dictionary;		
		private final String root;		
		private final String object;		
		private final String filesStatistics;
		
		Default(
			final String dictionary, 
			final String root, 
			final String object, 
			final String filesStatistics
		)
		{
			super();
			this.dictionary      = dictionary;
			this.root            = root;
			this.object          = object;
			this.filesStatistics = filesStatistics;
		}

		@Override
		public String dictionary()
		{
			return this.dictionary;
		}

		@Override
		public String root()
		{
			return this.root;
		}

		@Override
		public String object()
		{
			return this.object;
		}

		@Override
		public String filesStatistics()
		{
			return this.filesStatistics;
		}
		
	}
	
}
