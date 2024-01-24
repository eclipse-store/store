
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

import java.io.File;

public class MyRoot
{
	private final FileAssets fileAssets = new FileAssets(new File("assets"));
	
	public MyRoot()
	{
		super();
	}
	
	public FileAssets getFileAssets()
	{
		return this.fileAssets;
	}
}
