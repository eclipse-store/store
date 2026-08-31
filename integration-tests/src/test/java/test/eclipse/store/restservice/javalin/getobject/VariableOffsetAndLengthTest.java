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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.hc.core5.http.ParseException;
import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.store.storage.restadapter.types.ViewerObjectDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import test.eclipse.store.restservice.javalin.AbstractIntegrationTest;
import test.eclipse.store.restservice.javalin.UrlBuilder;

public class VariableOffsetAndLengthTest extends AbstractIntegrationTest {
    static Object[] input_noVariableLength = {testData, testData.threadStateEnum};

    static Stream<Object> input_noVariableLength() {
        return Arrays.stream(input_noVariableLength);
    }

    @ParameterizedTest
    @MethodSource("input_noVariableLength")
    public void noVariableLengthTest(final Object argument) throws IOException, ParseException
	{
        final ViewerObjectDescription objectDescription = AbstractIntegrationTest.testRequestNoParams(argument);
        assertNull(objectDescription.getVariableLength());
    }

    static Stream<Arguments> input_VariableLength() {
        return Stream.of(
                arguments(testData.stringEmpty, testData.stringEmpty.length()),
                arguments(testData.hashMap, testData.hashMap.size()),
                arguments(testData.arrayList, testData.arrayList.size()),
                arguments(testData.stringArray, testData.stringArray.length),
                arguments(testData.eqHashTable, testData.eqHashTable.intSize(),
                        arguments(testData.intArray, testData.intArray.length))
        );
    }

    @ParameterizedTest
    @MethodSource("input_VariableLength")
    public void variableLengthTest(final Object object, final int size) throws IOException, ParseException
	{
        final ViewerObjectDescription objectDescription = AbstractIntegrationTest.testRequestNoParams(object);
        assertNotNull(objectDescription.getVariableLength());
        assertLength(size, objectDescription.getVariableLength()[0]);
    }

    @Test
    public void variableLengthContent_StringArray_Test() throws IOException, ParseException
	{
        final int length = 1;
        final String[] testObject = testData.stringArray;
        final long oid = lookupObjectId(testObject);

        final String url = new UrlBuilder(oid).setVariableLength(length).build();
        final ViewerObjectDescription objectDescription = AbstractIntegrationTest.requestObjectByUrl(url);

        @SuppressWarnings("unchecked") final List<String> data = (List<String>) objectDescription.getData()[0];

        assertEquals(lookupObjectId(testObject[0]), Long.parseLong(data.get(0)));
    }

    @Test
    public void variableLengthOffsetContent_StringArray_Test() throws IOException, ParseException
	{
        final int length = 1;
        final int offset = 1;

        final String[] testObject = testData.stringArray;
        final long oid = lookupObjectId(testObject);

        final String url = new UrlBuilder(oid).setVariableLength(length).setVariableOffset(offset).build();
        final ViewerObjectDescription objectDescription = AbstractIntegrationTest.requestObjectByUrl(url);

        @SuppressWarnings("unchecked") final List<String> data = (List<String>) objectDescription.getData()[0];

        assertEquals(lookupObjectId(testObject[offset]), Long.parseLong(data.get(0)));
    }

    @Test
    public void variableLengthContent_EqHashTable_Test() throws IOException, ParseException
	{
        final int length = 1;
        final EqHashTable<Integer, String> testObject = testData.eqHashTable;
        final long oid = lookupObjectId(testObject);

        final String url = new UrlBuilder(oid).setVariableLength(length).build();
        final ViewerObjectDescription objectDescription = AbstractIntegrationTest.requestObjectByUrl(url);

        @SuppressWarnings("unchecked") final List<List<String>> data = (List<List<String>>) objectDescription.getData()[Integer.parseInt(objectDescription.getLength())];

        final String key = data.get(0).get(0);
        final String value = data.get(0).get(1);

        final KeyValue<Integer, String> x = testObject.at(0);

        assertEquals(lookupObjectId(x.key()), Long.parseLong(key));
        assertEquals(lookupObjectId(x.value()), Long.parseLong(value));
    }

    @Test
    public void variableLengthOffsetContent_EqHashTable_Test() throws IOException, ParseException
	{
        final int length = 1;
        final int offset = 1;

        final EqHashTable<Integer, String> testObject = testData.eqHashTable;
        final long oid = lookupObjectId(testObject);

        final String url = new UrlBuilder(oid).setVariableLength(length).setVariableOffset(offset).build();
        final ViewerObjectDescription objectDescription = AbstractIntegrationTest.requestObjectByUrl(url);

        @SuppressWarnings("unchecked") final List<List<String>> data = (List<List<String>>) objectDescription.getData()[Integer.parseInt(objectDescription.getLength())];

        final String key = data.get(0).get(0);
        final String value = data.get(0).get(1);

        final KeyValue<Integer, String> x = testObject.at(offset);

        assertEquals(lookupObjectId(x.key()), Long.parseLong(key));
        assertEquals(lookupObjectId(x.value()), Long.parseLong(value));
    }


    static IntStream range_0_16() {
        return IntStream.of(0, 1, 5, 10, 15, 16);
    }

    @ParameterizedTest
    @MethodSource("range_0_16")
    void testVariableOffset_intArray(final int argument) throws IllegalArgumentException, SecurityException, IOException, ParseException
	{
        this.testVariableOffset_Array(testData.intArray, argument);
    }

    @ParameterizedTest
    @MethodSource("range_0_16")
    void testVariableLength_intArray(final int argument) throws IllegalArgumentException, SecurityException, IOException, ParseException
	{
        this.testVariableLength_Array(testData.intArray, argument);
    }

    private void testVariableOffset_Array(final int[] objectTested, final int VariableOffset) throws IOException, ParseException
	{
        final long oid = lookupObjectId(objectTested);
        final String url = new UrlBuilder(oid).setVariableOffset(VariableOffset).build();
        final ViewerObjectDescription objectDescription = requestObjectByUrl(url);

        for (int i = VariableOffset; i < objectTested.length; i++) {
            final int actual = objectTested[i];
            @SuppressWarnings("unchecked") final ArrayList<String> data = (ArrayList<String>) objectDescription.getData()[0];
            final int expected = Integer.parseInt(data.get(i - VariableOffset));
            assertEquals(expected, actual);
        }
    }

    private void testVariableLength_Array(final int[] objectTested, final int VariableLength) throws IOException, ParseException
	{
        final long oid = lookupObjectId(objectTested);
        final String url = new UrlBuilder(oid).setVariableLength(VariableLength).build();
        final ViewerObjectDescription objectDescription = requestObjectByUrl(url);

        @SuppressWarnings("unchecked") final ArrayList<String> data = (ArrayList<String>) objectDescription.getData()[0];

        for (int i = 0; i < objectTested.length; i++) {
            if (i < VariableLength) {
                final int actual = objectTested[i];
                final int expected = Integer.parseInt(data.get(i));
                assertEquals(expected, actual);
            } else {
                final int x = i;
                assertThrows(IndexOutOfBoundsException.class, () -> {
                    @SuppressWarnings("unused") final String notFound = data.get(x);
                });
            }
        }

    }
}
