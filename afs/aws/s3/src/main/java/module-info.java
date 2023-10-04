/*-
 * #%L
 * EclipseStore Abstract File System AWS S3
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
module org.eclipse.store.afs.aws.s3
{
	exports org.eclipse.store.afs.aws.s3.types;
	
	provides org.eclipse.store.configuration.types.ConfigurationBasedCreator
	    with org.eclipse.store.afs.aws.s3.types.S3FileSystemCreator
	;
	
	requires transitive org.eclipse.store.afs.aws;
	requires transitive org.eclipse.store.afs.blobstore;
	requires transitive software.amazon.awssdk.http;
	requires transitive software.amazon.awssdk.services.s3;
}
