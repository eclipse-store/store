package org.eclipse.store.storage.embedded.tools.storage.migrator.typedictionary;

/*-
 * #%L
 * EclipseStore Storage Embedded Tools Storage Migrator
 * %%
 * Copyright (C) 2023 MicroStream Software
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

import java.nio.file.Paths;
import java.util.Objects;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;


public class UpdateTypeDictionary extends Recipe
{
	@Option(
		displayName = "Type dictionary file path",
		description = "Absolute path to the type dictionary file.",
		example = "/home/mystorage/PersistenceTypeDictionary.ptd"
	)
	private final String relativeFilePath;
	
	public UpdateTypeDictionary(final String relativeFilePath)
	{
		this.relativeFilePath = relativeFilePath;
	}
	
	@Override
	public String getDisplayName()
	{
		return "Update Type Dictionary";
	}
	
	@Override
	public String getDescription()
	{
		return "Updates the type dictionary file.";
	}
	
	public String getRelativeFilePath()
	{
		return this.relativeFilePath;
	}
	
	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor()
	{
		return new TypeDictionaryVisitor(Paths.get(this.relativeFilePath));
	}
	
	@Override
	public boolean equals(final Object o)
	{
		if(this == o)
			return true;
		if(o == null || this.getClass() != o.getClass())
			return false;
		if(!super.equals(o))
			return false;
		final UpdateTypeDictionary that = (UpdateTypeDictionary)o;
		return Objects.equals(this.relativeFilePath, that.relativeFilePath);
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(super.hashCode(), this.relativeFilePath);
	}
	
	@Override
	public String toString()
	{
		return "UpdateTypeDictionary{" +
			"filePath='" + this.relativeFilePath + '\'' +
			'}';
	}
}
