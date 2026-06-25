package test.eclipse.store.configuration;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;

import org.eclipse.serializer.configuration.types.ByteSize;
import org.eclipse.serializer.configuration.types.ByteUnit;
import org.eclipse.serializer.configuration.types.Configuration;
import org.junit.jupiter.api.Test;

class ConfigurationCommonsTest
{
	@Test
	void commons() {
		
		final Integer  int1      = 123;
		final Double   double1   = 1.23;
		final String   str1      = "str";
		final Boolean  boolean1  = true;
		final Duration duration1 = Duration.ofHours(1);
		final ByteSize bytesize1 = ByteSize.New(1.23, ByteUnit.MB);
		
		final Configuration config = Configuration.Builder()
			.set("a.b.c.int-1"   , int1.toString())
			.set("a.b.c.double-1", double1.toString())
			.set("a.b.string-1"  , str1)
			.set("a.b.boolean-1" , boolean1.toString())
			.set("a.duration-1"  , duration1.toString())
			.set("a.bytesize-1"  , bytesize1.toString())
			.buildConfiguration()
		;
		
		assertEquals(int1     , config.getInteger("a.b.c.int-1"));
		assertEquals(double1  , config.getDouble("a.b.c.double-1"));
		assertEquals(str1     , config.get("a.b.string-1"));
		assertEquals(boolean1 , config.getBoolean("a.b.boolean-1"));
		assertEquals(duration1, config.get("a.duration-1", Duration.class));
		assertEquals(bytesize1, config.get("a.bytesize-1", ByteSize.class));
		assertNull(config.get("not.present"));
		assertFalse(config.opt("not.present").isPresent());
		
		final Configuration child = config.child("a.b.c");
		assertEquals(int1     , child.getInteger("int-1"));
		assertEquals(double1  , child.getDouble("double-1"));
		assertNull(child.get("not-present"));
		
	}
}
