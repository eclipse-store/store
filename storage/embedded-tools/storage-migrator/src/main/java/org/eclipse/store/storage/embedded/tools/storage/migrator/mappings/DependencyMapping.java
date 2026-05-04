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

/**
 * Immutable mapping from a legacy MicroStream Maven coordinate ({@code groupId:artifactId}) to its
 * EclipseStore replacement.
 * <p>
 * Used by {@link DependencyMappings} as a single entry of the migration table consumed by the
 * {@code ConvertProject} OpenRewrite recipe.
 */
public class DependencyMapping
{
	private final String oldGroupId;
	private final String oldArtifactId;
	private final String newGroupId;
	private final String newArtifactId;

	/**
	 * Creates a new dependency mapping.
	 *
	 * @param oldGroupId    the legacy {@code groupId} to be replaced.
	 * @param oldArtifactId the legacy {@code artifactId} to be replaced.
	 * @param newGroupId    the new {@code groupId}.
	 * @param newArtifactId the new {@code artifactId}.
	 */
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

	/**
	 * @return the legacy {@code groupId}.
	 */
	public String getOldGroupId()
	{
		return this.oldGroupId;
	}

	/**
	 * @return the legacy {@code artifactId}.
	 */
	public String getOldArtifactId()
	{
		return this.oldArtifactId;
	}

	/**
	 * @return the new {@code groupId}.
	 */
	public String getNewGroupId()
	{
		return this.newGroupId;
	}

	/**
	 * @return the new {@code artifactId}.
	 */
	public String getNewArtifactId()
	{
		return this.newArtifactId;
	}
}
