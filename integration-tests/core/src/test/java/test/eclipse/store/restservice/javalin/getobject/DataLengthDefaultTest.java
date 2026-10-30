package test.eclipse.store.restservice.javalin.getobject;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.apache.hc.core5.http.ParseException;
import org.eclipse.store.storage.restadapter.types.ViewerObjectDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import test.eclipse.store.restservice.javalin.AbstractIntegrationTest;
import test.eclipse.store.restservice.javalin.UrlBuilder;


class DataLengthDefaultTest extends AbstractIntegrationTest {
    @Test
    void defaultDefaultValue() throws IOException, ParseException
	{
        final ViewerObjectDescription objectDescription = AbstractIntegrationTest.testRequestNoParams(testData.stringMember);

        assertEquals("This is a string", objectDescription.getData()[0]);
    }

    // Note: the SparkJava service exposed setDefaultDataLength() to change the server-side default
    // value length; the Javalin service has no such setter (it is not configurable via system
    // properties either), so the "defaultSet" scenario has no Javalin equivalent and is omitted.

    @ParameterizedTest
    @ValueSource(ints = {5, 13})
    void requestValueLengthControlsDataLength(final int argument) throws IOException, ParseException
	{
        final Object testObject = testData.stringMember;
        final long oid = storage.persistenceManager().lookupObjectId(testObject);
        final String url = new UrlBuilder(oid).setDataLength(argument).build();

        final ViewerObjectDescription objectDescription = AbstractIntegrationTest.requestObjectByUrl(url);

        assertEquals(argument, ((String) objectDescription.getData()[0]).length());
    }
}
