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

import java.io.IOException;

import javax.money.MonetaryAmount;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.eclipse.serializer.reference.Lazy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration that teaches the REST layer how to deal with two Vinoteca-specific types
 * that are not natively supported by Spring's default {@code ObjectMapper}:
 * <ul>
 *   <li>{@link MonetaryAmount} — serialized as a small JSON object
 *       ({@code { "amount": …, "currency": "EUR" }}) instead of relying on JavaMoney's default
 *       (and somewhat verbose) serialization;</li>
 *   <li>{@link Lazy} — serialized as JSON {@code null}; this short-circuits the lazy reference
 *       so the REST layer never accidentally triggers loading of large back-references, and
 *       avoids cycles such as {@code Wine → reviews → customer → orders → items → wine}.</li>
 * </ul>
 */
@Configuration
public class JacksonConfig
{
	/**
	 * Builds the custom Jackson module containing the {@link MonetaryAmount} and {@link Lazy}
	 * serializers.
	 *
	 * @return the module to be picked up automatically by Spring Boot's {@code ObjectMapper}
	 */
	@Bean
	public Module vinotecaJacksonModule()
	{
		final SimpleModule module = new SimpleModule("VinotecaModule");

		module.addSerializer(MonetaryAmount.class, new JsonSerializer<>()
		{
			@Override
			public void serialize(
				final MonetaryAmount value,
				final JsonGenerator  gen,
				final SerializerProvider serializers
			) throws IOException
			{
				gen.writeStartObject();
				gen.writeNumberField("amount", value.getNumber().doubleValue());
				gen.writeStringField("currency", value.getCurrency().getCurrencyCode());
				gen.writeEndObject();
			}
		});

		@SuppressWarnings("rawtypes")
		final JsonSerializer<Lazy> lazySerializer = new JsonSerializer<>()
		{
			@Override
			public void serialize(
				final Lazy              value,
				final JsonGenerator     gen,
				final SerializerProvider serializers
			) throws IOException
			{
				// Skip serializing lazy references to avoid loading and circular references
				gen.writeNull();
			}
		};
		module.addSerializer(Lazy.class, lazySerializer);

		return module;
	}
}
