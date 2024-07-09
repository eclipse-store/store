/*-
 * #%L
 * EclipseStore Abstract File System AWS
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
module org.eclipse.store.afs.aws
{
	exports org.eclipse.store.afs.aws.types;
	
	requires transitive org.eclipse.serializer.afs;
	requires transitive org.eclipse.serializer.configuration;
	requires transitive software.amazon.awssdk.auth;
	requires transitive software.amazon.awssdk.awscore;
	requires transitive software.amazon.awssdk.core;
	requires transitive software.amazon.awssdk.identity.spi;
	requires transitive software.amazon.awssdk.regions;
	requires transitive software.amazon.awssdk.utils;
}
