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
module microstream.cache
{
	exports one.microstream.cache.types;
	
	provides javax.cache.spi.CachingProvider
	    with one.microstream.cache.types.CachingProvider
	;
	
	requires transitive microstream.persistence.binary;
	requires transitive microstream.storage.embedded.configuration;
	requires transitive cache.api;
	requires transitive java.management;
}
