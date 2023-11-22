
package org.eclipse.store.examples.blobs;

/*-
 * #%L
 * EclipseStore Example Blobs
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

import java.util.UUID;

public class FileAsset
{
	private final String path;
	private final String name;
	private final String uuid;
	
	public FileAsset(final String path, final String name)
	{
		super();
		
		this.path = path;
		this.name = name;
		this.uuid = UUID.randomUUID().toString();
	}
	
	public String getPath()
	{
		return this.path;
	}
	
	public String getName()
	{
		return this.name;
	}
	
	public String getUUID()
	{
		return this.uuid;
	}
}
