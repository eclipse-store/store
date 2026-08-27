package org.eclipse.store.gigamap.issues;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Objects;

import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GigaMap687Test
{
	private static class TestObject
	{
		private String aString;

		@Override
		public boolean equals(Object object)
		{
			if (!(object instanceof TestObject test))
				return false;
			return Objects.equals(aString, test.aString);
		}

		@Override
		public int hashCode()
		{
			return Objects.hashCode(aString);
		}
	}

	private static final BinaryIndexerString<TestObject> testIndex = new BinaryIndexerString.Abstract<>()
	{
		@Override
		protected String getString(TestObject entity)
		{
			return entity.aString;
		}
	};

	@Test
	public void testGigaMapUpdateFirstEntity(@TempDir Path workDir)
	{
		try (var storage = EmbeddedStorage.start(workDir))
		{
			var gigaMap = GigaMap.<TestObject>Builder()
				.withBitmapIndex(testIndex)
				.build();

			storage.setRoot(gigaMap);
			storage.storeRoot();

			var test = new TestObject();
			test.aString = "test"; // if set to test1, it works as expected
			gigaMap.add(test);

			// it somehow seems to only affect the first object that is added to the gigamap
			// try not to add(test2)
			var test2 = new TestObject();
			test2.aString = "test2";
			gigaMap.add(test2);

			gigaMap.store();

			assertEquals(0, gigaMap.query(testIndex.is("test3")).count());

			gigaMap.update(test, aTest -> aTest.aString = "test3");
			gigaMap.store();

			assertEquals(1, gigaMap.query(testIndex.is("test3")).count());
		}
	}
}