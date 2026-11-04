package test.eclipse.store.restservice.javalin;

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

import java.io.IOException;

import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.junit.jupiter.api.Test;

class ExceptionsTest extends AbstractIntegrationTest {

    @Test
    void IntegrationTest_InvalidDataLength() throws IOException, ParseException
	{
        final Object testObject = testData;
        final long oid = lookupObjectId(testObject);
        final String url = new UrlBuilder(oid).setDataLength(-1).build();

        checkStatusCodeAncContentForUrl(url, HttpStatus.SC_NOT_FOUND, "valueLength");
    }

    @Test
    void IntegrationTest_InvalidObjectId() throws IOException, ParseException
	{
        final String url = "http://localhost:4567/store-data/object/NotALong";

        checkStatusCodeAncContentForUrl(url, HttpStatus.SC_NOT_FOUND, "invalid url parameter ObjectId");
    }

    @Test
    void IntegrationTest_InvalidLongValue() throws IOException, ParseException
	{
        final String url = "http://localhost:4567/store-data/object/00000?fixedOffset=hallo";

        checkStatusCodeAncContentForUrl(url, HttpStatus.SC_NOT_FOUND, "invalid url parameter fixedOffset");
    }

    @Test
	void IntegrationTest_InvalidReference() throws IOException, ParseException
	{
        final Object testObject = testData;
        final long oid = lookupObjectId(testObject);
        final String url = "http://localhost:4567/store-data/object/" + oid + "?references=hallo";

        checkStatusCodeAncContentForUrl(url, HttpStatus.SC_NOT_FOUND, "invalid url parameter reference");
    }

}
