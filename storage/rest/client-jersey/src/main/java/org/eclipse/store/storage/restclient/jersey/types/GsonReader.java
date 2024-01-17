
package org.eclipse.store.storage.restclient.jersey.types;

/*-
 * #%L
 * EclipseStore Storage REST Client Jersey
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.LongSerializationPolicy;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Provider;

@Provider
@Consumes(MediaType.APPLICATION_JSON)
public class GsonReader<T> implements MessageBodyReader<T>
{
	private Gson gson;
	
	public GsonReader()
	{
		super();
	}
	
	private Gson gson()
	{
		if(this.gson == null)
		{
			final JsonDeserializer<Date> deserializerDate = (json, typeOfT, context) ->
			{
				final String  data    = json.getAsString();
				final Instant instant = Instant.parse(data);
				return Date.from(instant);
			};
			
			this.gson = new GsonBuilder()
				.setLongSerializationPolicy(LongSerializationPolicy.STRING)
				.serializeNulls()
				.registerTypeAdapter(Date.class, deserializerDate)
				.create();
		}
		
		return this.gson;
	}
	
	@Override
	public boolean isReadable(
		final Class<?> type,
		final Type genericType,
		final Annotation[] annotations,
		final MediaType mediaType
	)
	{
		return true;
	}
	
	@Override
	public T readFrom(
		final Class<T> type,
		final Type genericType,
		final Annotation[] annotations,
		final MediaType mediaType,
		final MultivaluedMap<String, String> httpHeaders,
		final InputStream entityStream
	)
		throws IOException, WebApplicationException
	{
		try(final InputStreamReader reader = new InputStreamReader(
			entityStream,
			StandardCharsets.UTF_8)
		)
		{
			return this.gson().fromJson(reader, type);
		}
	}
	
}
