package org.eclipse.store.gigamap.issues;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLOutput;
import java.util.UUID;

import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

import com.github.javafaker.Faker;

public class GigaMap458Test {

	public static class TestObject {
		private long index;
		private final String id;
		private final String firstName;
		private String lastName;
		private final String email;
		private final int age;

		public TestObject(String id, String firstName, String lastName, String email, int age) {

			this.id = id;
			this.firstName = firstName;
			this.lastName = lastName;
			this.email = email;
			this.age = age;
		}

		public void setIndex(long index) {
			this.index = index;
		}

		public long getIndex() {
			return index;
		}

		public String getId() {
			return id;
		}

		public String getFirstName() {
			return firstName;
		}

		public String getLastName() {
			return lastName;
		}

		public int getAge() {
			return age;
		}

		public String getEmail() {
			return email;
		}

		public void setLastName(String lastName) {
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
					", email='" + email + '\'' +
					", age=" + age +
					"}\n";
		}
	}

	public static class TestObjectIndices {
		public final static BinaryIndexerString<TestObject> id = new BinaryIndexerString.Abstract<TestObject>() {
			@Override
			public String getString(TestObject object) {
				return object.id;
			}
		};

		public final static BinaryIndexerString<TestObject> firstName = new BinaryIndexerString.Abstract<TestObject>() {
			@Override
			public String getString(TestObject object) {
				return object.firstName;
			}
		};

		public final static BinaryIndexerString<TestObject> lastName = new BinaryIndexerString.Abstract<TestObject>() {
			@Override
			public String getString(TestObject object) {
				return object.lastName;
			}
		};

		public final static IndexerString<TestObject> email = new IndexerString.Abstract<TestObject>() {
			@Override
			public String getString(TestObject object) {
				return object.email;
			}
		};

		public final static IndexerInteger<TestObject> age = new IndexerInteger.Abstract<TestObject>() {
			@Override
			public Integer getInteger(TestObject object) {
				return object.age;
			}
		};

	}

	@Test
	public void testGigaMapUpdate() throws Exception {
		var startTest = System.nanoTime();

		final var gigaMap = GigaMap.<TestObject>Builder()
				.withBitmapIdentityIndex(TestObjectIndices.id)
				.withBitmapIndex(TestObjectIndices.age)
				.withBitmapIndex(TestObjectIndices.firstName)
				.withBitmapIndex(TestObjectIndices.lastName)
				.withBitmapIndex(TestObjectIndices.email)
				.build();

		TestObject o1 = new TestObject("61794079-8d91-47cf-8059-001094e3a73d", "Michale", "Farrell", "randi.nader@yahoo.com", 11);
		var o1Index = gigaMap.add(o1);

		TestObject o2 = new TestObject("9cb4b448-9dbc-46ad-9ecb-a14795ce535a", "Dorian", "Rempel", "rochel.rolfson@gmail.com", 42);
		var o2Index = gigaMap.add(o2);

		TestObject o3 = new TestObject("5f71a37b-968f-4a9a-9104-e142851fd007", "Heriberto", "Conn", "rosalyn.dietrich@hotmail.com", 62);
		var o3Index = gigaMap.add(o3);

		TestObject o4 = new TestObject("aad245da-ffb1-45bf-9af6-b575c90b29e9", "Charmaine", "Bailey", "douglas.schowalter@gmail.com", 36);
		var o4Index = gigaMap.add(o4);

		TestObject o5 = new TestObject("2446c5f4-cb84-48fa-875c-7a724d916042", "Rosendo", "Romaguera", "keenan.lubowitz@hotmail.com", 81);
		var o5Index = gigaMap.add(o5);

		System.out.println(gigaMap.toString(10));

		var newItem = new TestObject("a012ddf6-2218-4aa1-b432-12cc07d1260b", "John", "Doe", "john.dow@mail.com", 43);
		var addedItemId = gigaMap.add(newItem);
		newItem.setIndex(addedItemId);

		var itemToUpdate = gigaMap.get(addedItemId);
		if (itemToUpdate == null) {
			throw new Exception("Item not found");
		}
		var updatedItemId = gigaMap.update(itemToUpdate, e -> { // throw happens here
			e.setLastName("Doedoe");
		});

		System.out.println(
				"Time taken (update): " + (System.nanoTime() - startTest) / 1_000_000 + " ms, updated: "
						+ updatedItemId);
		assertEquals(addedItemId, updatedItemId);
	}
}
