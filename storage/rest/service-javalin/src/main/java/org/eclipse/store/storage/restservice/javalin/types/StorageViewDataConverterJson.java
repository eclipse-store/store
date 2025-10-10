package org.eclipse.store.storage.restservice.javalin.types;

/*-
 * #%L
 * EclipseStore Storage REST Service Sparkjava
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

import java.lang.reflect.Type;
import java.util.Date;

import org.eclipse.store.storage.restadapter.types.StorageViewDataConverter;

import com.google.gson.*;


public class StorageViewDataConverterJson implements StorageViewDataConverter
{
	private static final String   HTML_RESPONCE_CONTENT_TYPE = "application/json";
	private static final String[] FORMAT_STRINGS              = {HTML_RESPONCE_CONTENT_TYPE, "json"};

	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final Gson gson;


	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public StorageViewDataConverterJson()
	{
		super();


        final JsonSerializer<Date> serializerDate = new JsonSerializer<>()
        {
            @Override
            public JsonElement serialize(final Date src, final Type typeOfSrc, final JsonSerializationContext context)
            {
                return new JsonPrimitive(src.toInstant().toString());
            }
        };

        this.gson = new GsonBuilder()
            .setLongSerializationPolicy(LongSerializationPolicy.STRING )
            .serializeNulls()
            .registerTypeAdapter(Date.class, serializerDate)
            .create();
	}


	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	public Gson getGson()
	{
		return this.gson;
	}

	@Override
	public String convert(final Object object)
	{
		return this.gson.toJson(object);
	}

	@Override
	public String getHtmlResponseContentType()
	{
		return HTML_RESPONCE_CONTENT_TYPE;
	}


	@Override
	public String[] getFormatStrings()
	{
		return FORMAT_STRINGS;
	}
	
}

