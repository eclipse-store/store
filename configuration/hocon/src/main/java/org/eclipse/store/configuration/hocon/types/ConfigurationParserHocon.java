package org.eclipse.store.configuration.hocon.types;

/*-
 * #%L
 * EclipseStore Configuration Hocon
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import static org.eclipse.serializer.util.X.notNull;

import com.typesafe.config.ConfigFactory;

import org.eclipse.store.configuration.types.Configuration.Builder;
import org.eclipse.store.configuration.types.ConfigurationParser;

public interface ConfigurationParserHocon extends ConfigurationParser
{
	public static ConfigurationParserHocon New()
	{
		return new ConfigurationParserHocon.Default(
			ConfigurationMapperHocon.New()
		);
	}
	
	public static ConfigurationParserHocon New(
		final ConfigurationMapperHocon mapper
	)
	{
		return new ConfigurationParserHocon.Default(
			notNull(mapper)
		);
	}
	
	
	public static class Default implements ConfigurationParserHocon
	{
		private final ConfigurationMapperHocon mapper;

		Default(
			final ConfigurationMapperHocon mapper
		)
		{
			super();
			this.mapper = mapper;
		}
		
		@Override
		public Builder parseConfiguration(
			final Builder builder,
			final String  input
		)
		{
			return this.mapper.mapConfiguration(
				builder,
				ConfigFactory.parseString(input).root()
			);
		}
		
	}
	
}
