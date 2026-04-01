package org.eclipse.store.demo.vinoteca.config;

/*-
 * #%L
 * EclipseStore Demo Vinoteca
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JexlConfig
{
	@Bean
	public JexlEngine jexlEngine()
	{
		return new JexlBuilder()
			.permissions(
				JexlPermissions.parse(
					"org.eclipse.store.demo.vinoteca.model.*",
					"java.lang.*",
					"java.util.*",
					"java.util.stream.*",
					"java.math.*"
				)
			)
			.cache(128)
			.strict(true)
			.silent(false)
			.create();
	}
}
