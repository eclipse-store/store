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
import java.lang.reflect.Field;
import java.util.stream.IntStream;

import org.apache.hc.core5.http.ParseException;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinition;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinitionMember;
import org.eclipse.store.storage.restadapter.types.ViewerObjectDescription;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import test.eclipse.store.restservice.javalin.AbstractIntegrationTest;
import test.eclipse.store.restservice.javalin.UrlBuilder;

public class FixedOffsetAndLengthTest extends AbstractIntegrationTest {
	static IntStream range_0_16() {
		return IntStream.of(0,1,5,10,15,16);
	}

	/*
	 * Test FixedOffset parameter
	 */
	@ParameterizedTest
	@MethodSource("range_0_16")
	void testFixedOffset_Object(final int argument) throws IllegalArgumentException, IllegalAccessException,
			NoSuchFieldException, SecurityException, IOException, ParseException
	{
		this.testFixedOffset_Object(testData, argument);
	}

	@ParameterizedTest
	@MethodSource("range_0_16")
	void testFixedLength_Object(final int argument) throws IllegalArgumentException, IllegalAccessException,
			NoSuchFieldException, SecurityException, IOException, ParseException
	{
		this.testFixedLength_Object(testData, argument);
	}

	private void testFixedLength_Object(final Object objectTested, final int fixedLength) throws IOException,
			NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ParseException
	{
		final long oid = lookupObjectId(objectTested);
		final long tid = lookupTypeId(objectTested);

		final String url = new UrlBuilder(oid).setFixedLength(fixedLength).build();
		final ViewerObjectDescription objectDescription = requestObjectByUrl(url);

		assertOid(oid, objectDescription.getObjectId());
		assertTid(tid, objectDescription.getTypeId());

		final PersistenceTypeDefinition ptd = storage.typeDictionary()
				.lookupTypeById(Long.parseLong(objectDescription.getTypeId()));
		final int numMembers = ptd.instanceMembers().intSize();

		assertLength(numMembers, objectDescription.getLength());

		final int expectedValueCount = Math.min(ptd.instanceMembers().intSize(),fixedLength);
		assertEquals(expectedValueCount, objectDescription.getData().length);

		final Object data[] = objectDescription.getData();
		for (int i = 0; i < expectedValueCount; i++) {
			final PersistenceTypeDefinitionMember m = ptd.instanceMembers().at(i);
			final Object actual = data[i];

			final String name = m.name();
			final Field f = objectTested.getClass().getField(name);
			final Object d = f.get(objectTested);

			if (m.isReference()) {
				if (d == null) {
					assertEquals("0", actual);
				} else {
					final long id = lookupObjectId(d);
					assertEquals(Long.toString(id), actual);
				}
			} else {
				assertEquals(d.toString(), actual);
			}
		}

	}

	void testFixedOffset_Object(final Object objectTested, final long fixedOffset) throws IllegalArgumentException,
			IllegalAccessException, NoSuchFieldException, SecurityException, IOException, ParseException
	{
		final long oid = lookupObjectId(objectTested);
		final long tid = lookupTypeId(objectTested);

		final String url = new UrlBuilder(oid).setFixedOffset(fixedOffset).build();
		final ViewerObjectDescription objectDescription = requestObjectByUrl(url);

		assertOid(oid, objectDescription.getObjectId());
		assertTid(tid, objectDescription.getTypeId());

		final PersistenceTypeDefinition ptd = storage.typeDictionary()
				.lookupTypeById(Long.parseLong(objectDescription.getTypeId()));
		final int numMembers = ptd.instanceMembers().intSize();

		assertLength(numMembers, objectDescription.getLength());

		final Object data[] = objectDescription.getData();
		for (int i = (int) fixedOffset; i < numMembers; i++) {
			final PersistenceTypeDefinitionMember m = ptd.instanceMembers().at(i);
			final Object actual = data[i - (int) fixedOffset];

			final String name = m.name();
			final Field f = objectTested.getClass().getField(name);
			final Object d = f.get(objectTested);

			if (m.isReference()) {
				if (d == null) {
					assertEquals("0", actual);
				} else {
					final long id = lookupObjectId(d);
					assertEquals(Long.toString(id), actual);
				}
			} else {
				assertEquals(d.toString(), actual);
			}
		}
	}
}
