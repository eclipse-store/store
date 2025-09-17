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

import java.nio.file.Path;

import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class UpdateBinaryIndex462Test
{

	private static class TestObject
	{
		private long index;
		private final String id;
		private final String firstName;
		private String lastName;

		public TestObject(String id, String firstName, String lastName)
		{

			this.id = id;
			this.firstName = firstName;
			this.lastName = lastName;
		}

		public void setIndex(long index)
		{
			this.index = index;
		}

		public String getId()
		{
			return id;
		}

		public String getFirstName()
		{
			return firstName;
		}

		public String getLastName()
		{
			return lastName;
		}

		public void setLastName(String lastName)
		{
			this.lastName = lastName;
		}

		@Override
		public String toString()
		{
			return "TestObject{" +
					"index=" + index +
					", id='" + id + '\'' +
					", firstName='" + firstName + '\'' +
					", lastName='" + lastName + '\'' +
					"}\n";
		}
	}

	static class TestObjectIndices
	{
		public final static BinaryIndexerString<TestObject> id = new BinaryIndexerString.Abstract<>()
		{
			@Override
			public String getString(TestObject object)
			{
				return object.getId();
			}
		};

		public final static BinaryIndexerString<TestObject> firstName = new BinaryIndexerString.Abstract<>()
		{
			@Override
			public String getString(TestObject object)
			{
				return object.getFirstName();
			}
		};

		public final static BinaryIndexerString<TestObject> lastName = new BinaryIndexerString.Abstract<>()
		{
			@Override
			public String getString(TestObject object)
			{
				return object.getLastName();
			}
		};


	}

	@Test
	public void testGigaMapUpdate_stored(@TempDir Path workDir) throws Exception
	{

		final var gigaMap = GigaMap.<TestObject>Builder()
				.withBitmapIdentityIndex(TestObjectIndices.id)
				.withBitmapIndex(TestObjectIndices.firstName)
				.withBitmapIndex(TestObjectIndices.lastName)
				.build();

		TestObject o1 = new TestObject("61794079-8d91-47cf-8059-001094e3a73d", "Michale", "Farrell");

		var newItem = new TestObject("a012ddf6-2218-4aa1-b432-12cc07d1260b", "John", "Doe");
		var addedItemId = gigaMap.add(newItem);
		newItem.setIndex(addedItemId);

		//store into storage
		try (var manager = EmbeddedStorage.start(gigaMap, workDir)) {
		}

		//load from storage
		try (var manager = EmbeddedStorage.start(workDir)) {
			GigaMap<TestObject> loadedMap = (GigaMap<TestObject>) manager.root();

			var itemToUpdate = loadedMap.get(addedItemId);

			loadedMap.update(itemToUpdate, e -> { // throw happens here
				e.setLastName("Doedoe");
			});

		}


	}
}
