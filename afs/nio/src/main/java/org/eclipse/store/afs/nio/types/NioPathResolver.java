package org.eclipse.store.afs.nio.types;

/*-
 * #%L
 * EclipseStore Abstract File System - Java NIO
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

import org.eclipse.serializer.io.XIO;

import static org.eclipse.serializer.util.X.notNull;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

@FunctionalInterface
public interface NioPathResolver
{
	public Path resolvePath(final String... pathElements);
	
	
	public static NioPathResolver New()
	{
		return new Default(
			FileSystems.getDefault()
		);
	}
	
	public static NioPathResolver New(final FileSystem fileSystem)
	{
		return new Default(
			notNull(fileSystem)
		);
	}
	
	
	public static class Default implements NioPathResolver
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final FileSystem fileSystem;
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default(final FileSystem fileSystem)
		{
			super();
			this.fileSystem = fileSystem;
		}
		 
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public Path resolvePath(final String... pathElements)
		{
			return XIO.Path(this.fileSystem, pathElements);
		}
	}
	
}
