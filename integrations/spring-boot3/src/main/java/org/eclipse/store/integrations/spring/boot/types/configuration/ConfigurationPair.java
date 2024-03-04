package org.eclipse.store.integrations.spring.boot.types.configuration;

/*-
 * #%L
 * spring-boot3
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
 * The {@code ConfigurationPair} record represents a key-value pair in a configuration.
 * It is used to store configuration parameters for the application.
 *
 * <p>Here's an example of how to use this record:</p>
 * <pre>
 * <code>
 * ConfigurationPair pair = new ConfigurationPair("key", "value");
 * </code>
 * </pre>
 * <p>
 * In this example, a new {@code ConfigurationPair} is created with the key "key" and the value "value".
 */
public record ConfigurationPair(String key, String value)
{

}
