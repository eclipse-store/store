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
import java.util.Arrays;
import java.util.stream.IntStream;

import org.apache.hc.core5.http.ParseException;
import org.eclipse.store.storage.restadapter.types.ViewerObjectDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import test.eclipse.store.restservice.javalin.AbstractIntegrationTest;
import test.eclipse.store.restservice.javalin.UrlBuilder;

public class DataLengthTest extends AbstractIntegrationTest
{
    static int[] valueLengthTestArguments = {0, 1, 14, 15, 16};

    static IntStream valueLength() {
        return Arrays.stream(valueLengthTestArguments);
    }

    /*
     * Test valueLength for simple string object
     */
    @ParameterizedTest
    @MethodSource("valueLength")
	public void testValueLength_String(final int argument) throws IOException, ParseException
	{
      final Object testObject = testData.stringMember;
      final int dataLength = argument;

      final long oid = lookupObjectId(testObject);
      final String url = new UrlBuilder(oid).setDataLength(dataLength).build();
      final ViewerObjectDescription objectDescription = requestObjectByUrl(url);

      final String stringValue = testObject.toString();
      final int expectedLength = Math.max(Math.min(stringValue.length(), dataLength), 0);
      final int actualLength = ((String) objectDescription.getData()[0]).length();

      assertEquals(expectedLength, actualLength);
      final String expectedContent = stringValue.substring(0, Math.max(Math.min(dataLength, stringValue.length()), 0));

      assertEquals(expectedContent, objectDescription.getData()[0]);
	}

    /*
     * Test valueLength for simple string object as an included reference
     */
    @ParameterizedTest
    @MethodSource("valueLength")
	public void testValueLength_IncludeReferenceString(final int argument) throws IOException, ParseException
	{
      final Object testObject = testData;
      final int dataLength = argument;

      final long oid = lookupObjectId(testObject);
      final String url = new UrlBuilder(oid).setDataLength(dataLength).setReferences(true).build();
      final ViewerObjectDescription objectDescription = requestObjectByUrl(url);

      final String stringValue = testData.stringMember;
      final int expectedLength = Math.max(Math.min(stringValue.length(), dataLength), 0);
      final int actualLength = ((String) objectDescription.getReferences()[0].getData()[0]).length();

      assertEquals(expectedLength, actualLength);
      final String expectedContent = stringValue.substring(0, Math.max(Math.min(dataLength, stringValue.length()), 0));

      assertEquals(expectedContent, objectDescription.getReferences()[0].getData()[0]);
	}

    @ParameterizedTest
    @CsvSource({
    		" 0,	''",
            " 1,	T",
            "15, 	This is a strin",
            "16, 	This is a string",
            "17,	This is a string"
    })
    void testValueLength_Bounds(final int dataLength, final String result) throws IOException, ParseException
	{
        final Object testObject = testData.stringMember;
        final long oid = lookupObjectId(testObject);
        final String url = new UrlBuilder(oid).setDataLength(dataLength).build();
        final ViewerObjectDescription objectDescription = requestObjectByUrl(url);

        assertEquals(result, objectDescription.getData()[0]);
    }

    @Test
    void testValueLength_MaxLong() throws IOException, ParseException
	{
        final Object testObject = testData.stringMember;
        final long oid = lookupObjectId(testObject);
        final String url = new UrlBuilder(oid).setDataLength(Long.MAX_VALUE).build();
        final ViewerObjectDescription objectDescription = requestObjectByUrl(url);

        assertEquals("This is a string", objectDescription.getData()[0]);
    }
}
