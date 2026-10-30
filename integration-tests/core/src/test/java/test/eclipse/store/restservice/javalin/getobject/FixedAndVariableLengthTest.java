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
import java.math.BigDecimal;

import org.apache.hc.core5.http.ParseException;
import org.eclipse.store.storage.restadapter.types.ViewerObjectDescription;
import org.junit.jupiter.api.Test;

import test.eclipse.store.restservice.javalin.AbstractIntegrationTest;

class FixedAndVariableLengthTest extends AbstractIntegrationTest {

	@Test
	void testGetStringObject() throws IOException, ParseException
	{
		final ViewerObjectDescription objectDescription = testRequestNoParams(testData.stringMember);

		assertLength(1, objectDescription.getLength());
		assertLength(0, objectDescription.getVariableLength()[0]);
		assertEquals("This is a string", objectDescription.getData()[0]);
	}


	@Test
	void testGetStringEmptyObject() throws IOException, ParseException
	{
		final ViewerObjectDescription objectDescription = testRequestNoParams(testData.stringEmpty);

		assertLength(1, objectDescription.getLength());
		assertEquals("", objectDescription.getData()[0]);
	}

	@Test
	void testGetBigDezObject() throws IOException, ParseException
	{
		final ViewerObjectDescription objectDescription = testRequestNoParams(testData.bigDezValue);

		assertLength(1, objectDescription.getLength());
		assertLength(0, objectDescription.getVariableLength()[0]);
		assertEquals(testData.bigDezValue, new BigDecimal((String) objectDescription.getData()[0]));
	}

	@Test
	void testGetBigDez2Object() throws IOException, ParseException
	{
		final ViewerObjectDescription objectDescription = testRequestNoParams(testData.bigDezValue2);

		assertLength(1, objectDescription.getLength());
		assertLength(0, objectDescription.getVariableLength()[0]);
		assertEquals(testData.bigDezValue2, new BigDecimal((String) objectDescription.getData()[0]));
	}

	@Test
	void testGetHashMapObject() throws IOException, ParseException
	{
		final ViewerObjectDescription objectDescription = testRequestNoParams(testData.hashMap);

		assertLength(0, objectDescription.getLength());
		assertLength(3, objectDescription.getVariableLength()[0]);
	}

	@Test
	void testGetArrayListObject() throws IOException, ParseException
	{
		final ViewerObjectDescription objectDescription = testRequestNoParams(testData.arrayList);

		assertLength(0, objectDescription.getLength());
		assertLength(5, objectDescription.getVariableLength()[0]);
	}

	@Test
	void testGetIntArrayObject() throws IOException, ParseException
	{
		final ViewerObjectDescription objectDescription = testRequestNoParams(testData.intArray);

		assertLength(10, objectDescription.getVariableLength()[0]);
	}

	@Test
	void testGetIntConstant() throws IOException, ParseException
	{
		final ViewerObjectDescription objectDescription = testRequestNoParams(testData.anIntegerConstant);

		assertLength(1, objectDescription.getLength());
	}

	//@Test
	void testGetRootClass() throws IOException, ParseException
	{
		final ViewerObjectDescription objectDescription = testRequestNoParams(testData);

		final int fieldCount = testData.getClass().getDeclaredFields().length;
		assertLength(fieldCount, objectDescription.getLength());
	}
}

