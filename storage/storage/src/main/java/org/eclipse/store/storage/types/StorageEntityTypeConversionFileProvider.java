package org.eclipse.store.storage.types;

import static org.eclipse.serializer.chars.XChars.notEmpty;

/*-
 * #%L
 * EclipseStore Storage
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

import static org.eclipse.serializer.util.X.notNull;

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.afs.types.AWritableFile;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinition;


public interface StorageEntityTypeConversionFileProvider
{
	public AWritableFile provideConversionFile(PersistenceTypeDefinition typeDescription, AFile sourceFile);
	
	/**
	 * Pseudo-constructor method to create a new {@link StorageEntityTypeConversionFileProvider}.
	 * @param directory the target directory
	 * @param fileSuffix the suffix to use for the created files
	 * @return a new {@link StorageEntityTypeConversionFileProvider}
	 */
	public static StorageEntityTypeConversionFileProvider New(
		final ADirectory directory ,
		final String     fileSuffix
	)
	{
		return new StorageEntityTypeConversionFileProvider.Default(
			notNull(directory),
			notEmpty(fileSuffix)
		);
	}



	public final class Default implements StorageEntityTypeConversionFileProvider
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final ADirectory directory ;
		private final String     fileSuffix;




		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		public Default(final ADirectory directory, final String fileSuffix)
		{
			super();
			this.directory  = notNull(directory);
			this.fileSuffix = fileSuffix        ;
		}



		///////////////////////////////////////////////////////////////////////////
		// declared methods //
		/////////////////////

		public final String fileSuffix()
		{
			return this.fileSuffix;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public AWritableFile provideConversionFile(
			final PersistenceTypeDefinition typeDescription,
			final AFile                     sourceFile
		)
		{
			// TypeId must be included since only that is the unique identifier of a type.
			
			final String fileName = typeDescription.typeName() + "_" + typeDescription.typeId();
			final AFile targetFile = this.directory.ensureFile(fileName, this.fileSuffix);
			targetFile.ensureExists();
			return targetFile.useWriting();
		}

	}

}
