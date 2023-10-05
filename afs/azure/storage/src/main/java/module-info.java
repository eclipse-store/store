/*-
 * #%L
 * EclipseStore Abstract File System Azure Storage
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
module org.eclipse.store.afs.azure.storage
{
	exports org.eclipse.store.afs.azure.storage.types;
	
	provides org.eclipse.serializer.configuration.types.ConfigurationBasedCreator
	    with org.eclipse.store.afs.azure.storage.types.AzureStorageFileSystemCreator
	;
	
	requires transitive org.eclipse.serializer.configuration;
	requires transitive org.eclipse.store.afs.blobstore;
	requires transitive com.azure.core;
	requires transitive com.azure.storage.blob;
	requires transitive com.azure.storage.common;
}
