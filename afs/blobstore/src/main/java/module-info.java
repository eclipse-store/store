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
module org.eclipse.store.afs.blobstore
{
	exports org.eclipse.store.afs.blobstore.types;
	
	requires transitive org.eclipse.serializer.afs;
}
