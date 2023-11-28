package org.eclipse.store.cache.hibernate.types;

/*-
 * #%L
 * microstream-cache-hibernate
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

public interface ConfigurationPropertyNames
{
	public static final String PREFIX                      = "hibernate.cache.microstream.";
	                                                 
	public static final String CACHE_MANAGER               = PREFIX + "cache-manager";
	public static final String MISSING_CACHE_STRATEGY      = PREFIX + "missing-cache-strategy";
	public static final String CACHE_LOCK_TIMEOUT          = PREFIX + "cache-lock-timeout";
	public static final String CONFIGURATION_RESOURCE_NAME = PREFIX + "configuration-resource-name";
}
