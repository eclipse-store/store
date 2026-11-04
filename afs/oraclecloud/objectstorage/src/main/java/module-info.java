/*-
 * #%L
 * EclipseStore Abstract File System Oracle Cloud ObjectStorage
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
 * @deprecated Deprecated since 4.3.0 and scheduled for removal in 5.0.0. There is no replacement.
 */
@Deprecated(since = "4.3.0", forRemoval = true)
module org.eclipse.store.afs.oraclecloud.objectstorage
{
	exports org.eclipse.store.afs.oraclecloud.objectstorage.types;
	
	provides org.eclipse.serializer.configuration.types.ConfigurationBasedCreator
	    with org.eclipse.store.afs.oraclecloud.objectstorage.types.OracleCloudObjectStorageFileSystemCreator
	;
	
	requires transitive org.eclipse.serializer.configuration;
	requires transitive org.eclipse.store.afs.blobstore;
	requires transitive oci.java.sdk.common;
	requires transitive oci.java.sdk.objectstorage.extensions;
	requires transitive oci.java.sdk.objectstorage.generated;
	requires oci.java.sdk.common.httpclient;
}
