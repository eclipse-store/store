package org.eclipse.store.afs.blobstore.types;

/*-
 * #%L
 * EclipseStore Abstract File System Blobstore
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

public interface BlobStorePathVisitor
{
	public void visitDirectory(BlobStorePath parent, String directoryName);

	public void visitFile(BlobStorePath parent, String fileName);
}
