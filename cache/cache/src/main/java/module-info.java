/*-
 * #%L
 * microstream-cache
 * %%
 * Copyright (C) 2019 - 2022 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */
module org.eclipse.store.cache
{
	exports org.eclipse.store.cache.types;
	
	provides javax.cache.spi.CachingProvider
	    with org.eclipse.store.cache.types.CachingProvider
	;
	
	requires transitive org.eclipse.serializer.persistence.binary;
	requires transitive org.eclipse.store.storage.embedded.configuration;
	requires transitive cache.api;
	requires transitive java.management;
	requires org.eclipse.store.storage.embedded;
	requires org.eclipse.serializer.configuration;
	requires org.eclipse.serializer;
	requires org.eclipse.serializer.base;
}
