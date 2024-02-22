package test.eclipse.store.integrations.spring.boot;

/*-
 * #%L
 * EclipseStore Integrations SpringBoot
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

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * No component scan, rather configuration and enable auto configurations.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TestApplication
{
}
