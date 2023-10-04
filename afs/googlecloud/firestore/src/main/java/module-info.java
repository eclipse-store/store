/*-
 * #%L
 * EclipseStore Abstract File System Google Cloud Firestore
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
module org.eclipse.store.afs.googlecloud.firestore
{
	exports org.eclipse.store.afs.googlecloud.firestore.types;
	
	provides org.eclipse.store.configuration.types.ConfigurationBasedCreator
	    with org.eclipse.store.afs.googlecloud.firestore.types.GoogleCloudFirestoreFileSystemCreator
	;
	
	requires transitive org.eclipse.store.configuration;
	requires transitive org.eclipse.store.afs.blobstore;
	requires transitive com.google.api.apicommon;
	requires transitive com.google.auth;
	requires transitive com.google.auth.oauth2;
	requires transitive com.google.protobuf;
	requires transitive gax;
	requires transitive google.cloud.core;
	requires transitive google.cloud.firestore;
}
