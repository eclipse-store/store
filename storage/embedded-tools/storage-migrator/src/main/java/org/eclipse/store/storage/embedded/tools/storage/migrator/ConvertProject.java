package org.eclipse.store.storage.embedded.tools.storage.migrator;

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

import static org.eclipse.serializer.util.X.coalesce;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.store.storage.embedded.tools.storage.migrator.mappings.DependencyMapping;
import org.eclipse.store.storage.embedded.tools.storage.migrator.mappings.DependencyMappings;
import org.eclipse.store.storage.embedded.tools.storage.migrator.mappings.PackageMappings;
import org.eclipse.store.storage.embedded.tools.storage.migrator.typedictionary.UpdateTypeDictionary;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.maven.AddDependency;
import org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.EqualsAndHashCode;


@EqualsAndHashCode(callSuper = false)
public class ConvertProject extends Recipe
{
	private final static String ECLIPSE_STORE_VERSION     = "eclipseStoreVersion";
	private final static String TYPE_DICTIONARY_FILE_PATH = "typeDictionaryRelativeFilePath";
	
	@Option(
		displayName = "Version of EclipseStore to use",
		description = "An exact version number or node-style semver selector used to select the version number.",
		example = "1.0.0",
		required = false
	)
	private final String eclipseStoreVersion;
	
	@Option(
		displayName = "Type dictionary file path",
		description = "Relative path to the type dictionary file.",
		example = "/home/mystorage/PersistenceTypeDictionary.ptd",
		required = false
	)
	private final String typeDictionaryRelativeFilePath;
	
	@JsonCreator
	public ConvertProject(
		@JsonProperty(ECLIPSE_STORE_VERSION)     final String eclipseStoreVersion,
		@JsonProperty(TYPE_DICTIONARY_FILE_PATH) final String typeDictionaryRelativeFilePath
	)
	{
		super();
		this.eclipseStoreVersion            = coalesce(
			eclipseStoreVersion,
			System.getProperty(ECLIPSE_STORE_VERSION)
		);
		this.typeDictionaryRelativeFilePath = coalesce(
			typeDictionaryRelativeFilePath,
			System.getProperty(TYPE_DICTIONARY_FILE_PATH)
		);
	}
	
	@Override
	public String getDisplayName()
	{
		return "Convert sources and storage";
	}
	
	@Override
	public String getDescription()
	{
		return "Converts the project's codebase and the storage type dictionary from MicroStream to EclipseStore.";
	}
	
	public String getEclipseStoreVersion()
	{
		return this.eclipseStoreVersion;
	}
	
	public String getTypeDictionaryRelativeFilePath()
	{
		return this.typeDictionaryRelativeFilePath;
	}
	
	@Override
	public List<Recipe> getRecipeList()
	{
		final List<Recipe> list = new ArrayList<>();
		
		if(this.eclipseStoreVersion != null)
		{
			list.add(this.createAddDependency());
			
			DependencyMappings.INSTANCE.forEach(
				mapping -> list.add(this.createChangeDependency(mapping))
			);
			
			PackageMappings.INSTANCE.forEach(
				mapping -> list.add(this.createChangePackage(mapping))
			);
		}
		
		if(this.typeDictionaryRelativeFilePath != null)
		{
			list.add(new UpdateTypeDictionary(this.typeDictionaryRelativeFilePath));
		}
		
		return list;
	}

	private AddDependency createAddDependency()
	{
		// Detects usage of serializer utilities and adds extracted dependency.
		return new AddDependency(
			"org.eclipse.serializer",
			"serializer",
			this.eclipseStoreVersion,
			null,
			null,
			null,
			"one.microstream.persistence.binary.util.*",
			null,
			null,
			null,
			null,
			null
		);
	}

	private ChangeDependencyGroupIdAndArtifactId createChangeDependency(final DependencyMapping mapping)
	{
		return new ChangeDependencyGroupIdAndArtifactId(
			mapping.getOldGroupId(),
			mapping.getOldArtifactId(),
			mapping.getNewGroupId(),
			mapping.getNewArtifactId(),
			this.eclipseStoreVersion,
			null
		);
	}

	private ChangePackage createChangePackage(final Map.Entry<String, String> mapping)
	{
		return new ChangePackage(
			mapping.getKey(),
			mapping.getValue(),
			Boolean.FALSE
		);
	}
	
}
