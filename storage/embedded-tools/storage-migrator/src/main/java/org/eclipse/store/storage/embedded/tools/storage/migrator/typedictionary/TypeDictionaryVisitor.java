package org.eclipse.store.storage.embedded.tools.storage.migrator.typedictionary;

/*-
 * #%L
 * EclipseStore Storage Embedded Tools Storage Migrator
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

import java.nio.file.Path;

import org.eclipse.serializer.collections.types.XGettingSequence;
import org.eclipse.serializer.persistence.binary.types.BinaryPersistenceFoundation;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryEntry;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;


public class TypeDictionaryVisitor extends PlainTextVisitor<ExecutionContext>
{
	private final Path relativeFilePath;
	
	public TypeDictionaryVisitor(final Path relativeFilePath)
	{
		this.relativeFilePath = relativeFilePath;
	}
	
	@Override
	public boolean isAcceptable(final SourceFile sourceFile, final ExecutionContext executionContext)
	{
		return super.isAcceptable(sourceFile, executionContext)
			&& sourceFile.getSourcePath().equals(this.relativeFilePath);
	}
	
	@Override
	public PlainText visitText(final PlainText plainText, final ExecutionContext executionContext)
	{
		final BinaryPersistenceFoundation<?> persistenceFoundation = BinaryPersistenceFoundation.New();
		persistenceFoundation.setTypeNameMapper(
			new RewriteTypeNameMapper(persistenceFoundation.getTypeNameMapper())
		);
		// mapping is done while parsing by RewriteTypeNameMapper
		final XGettingSequence<? extends PersistenceTypeDictionaryEntry> entries =
			persistenceFoundation.getTypeDictionaryParser().parseTypeDictionaryEntries(plainText.getText());
		final String text = persistenceFoundation.getTypeDictionaryAssembler().assemble(
			persistenceFoundation.getTypeDictionaryBuilder().buildTypeDictionary(entries)
		);
		
		return plainText.withText(text);
	}
	
}
