
package org.eclipse.store.integrations.cdi.types.cache;

/*-
 * #%L
 * EclipseStore Integrations CDI 4
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;


/**
 * Defines a cache managed by EclipseStore:
 * <a href="https://docs.eclipsestore.io/manual/cache/getting-started.html">Getting started for Cache</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Qualifier
public @interface StorageCache
{
	/**
	 * the name of the managed {@link javax.cache.Cache} to acquire.
	 * 
	 * @return the cache name
	 */
	@Nonbinding
	String value() default "jcache";
}
