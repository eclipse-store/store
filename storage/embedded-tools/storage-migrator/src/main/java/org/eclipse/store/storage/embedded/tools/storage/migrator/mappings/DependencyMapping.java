package org.eclipse.store.storage.embedded.tools.storage.migrator.mappings;

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

public class DependencyMapping
{
	private final String oldGroupId;
	private final String oldArtifactId;
	private final String newGroupId;
	private final String newArtifactId;
	
	public DependencyMapping(
		final String oldGroupId,
		final String oldArtifactId,
		final String newGroupId,
		final String newArtifactId)
	{
		this.oldGroupId    = oldGroupId;
		this.oldArtifactId = oldArtifactId;
		this.newGroupId    = newGroupId;
		this.newArtifactId = newArtifactId;
	}
	
	public String getOldGroupId()
	{
		return this.oldGroupId;
	}
	
	public String getOldArtifactId()
	{
		return this.oldArtifactId;
	}
	
	public String getNewGroupId()
	{
		return this.newGroupId;
	}
	
	public String getNewArtifactId()
	{
		return this.newArtifactId;
	}
}
