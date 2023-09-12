package org.eclipse.store.afs.sql.types;

/*-
 * #%L
 * Eclipse Store Abstract File System - SQL
 * %%
 * Copyright (C) 2023 Eclipse Foundation
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import static java.util.stream.Collectors.joining;
import static org.eclipse.serializer.util.X.notNull;

import java.util.Arrays;

import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.collections.XArrays;


public interface SqlPath
{
	public String[] pathElements();

	public String identifier();

	public String fullQualifiedName();

	public SqlPath parentPath();


	public static String[] splitPath(
		final String fullQualifiedPath
	)
	{
		return XChars.splitSimple(fullQualifiedPath, getSeparatorString());
	}

	public static SqlPath New(
		final String... pathElements
	)
	{
		return new Default(
			notNull(pathElements)
		);
	}

	public static SqlPathSeparatorProvider set(final SqlPathSeparatorProvider sqlPathSeparatorProvider)
	{
		return Static.set(sqlPathSeparatorProvider);
	}

	public static SqlPathSeparatorProvider get()
	{
		return Static.get();
	}

	static String getSeparatorString()
	{
		return Static.get().getSqlPathSeparator();
	}

	static char getSeparatorChar()
	{
		return Static.get().getSqlPathSeparatorChar();
	}


	public final class Static
	{
		static SqlPathSeparatorProvider pathSeparatorProvider = SqlPathSeparatorProvider.New();

		static synchronized SqlPathSeparatorProvider set(final SqlPathSeparatorProvider sqlPathSeparatorProvider)
		{
			pathSeparatorProvider = sqlPathSeparatorProvider;
			return pathSeparatorProvider;
		}

		static synchronized SqlPathSeparatorProvider get()
		{
			return pathSeparatorProvider;
		}

	}
	

	public final static class Default implements SqlPath
	{
		private final String[] pathElements     ;
		private       String   fullQualifiedName;

		Default(
			final String[] pathElements
		)
		{
			super();
			this.pathElements = pathElements;
		}

		@Override
		public String[] pathElements()
		{
			return this.pathElements;
		}

		@Override
		public String identifier()
		{
			return this.pathElements[this.pathElements.length - 1];
		}

		@Override
		public String fullQualifiedName()
		{
			if(this.fullQualifiedName == null)
			{
				this.fullQualifiedName = Arrays
					.stream(this.pathElements)
					.collect(joining(SqlPath.getSeparatorString()))
				;
			}

			return this.fullQualifiedName;
		}

		@Override
		public SqlPath parentPath()
		{
			return this.pathElements.length > 1
				? new SqlPath.Default(
					XArrays.copyRange(this.pathElements, 0, this.pathElements.length - 1)
				)
				: null;
		}

	}

}
