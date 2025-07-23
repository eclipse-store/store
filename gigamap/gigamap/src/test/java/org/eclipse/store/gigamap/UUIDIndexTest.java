package org.eclipse.store.gigamap;

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

import org.eclipse.store.gigamap.types.BinaryIndexerUUID;
import org.eclipse.store.gigamap.types.GigaMap;

import java.util.Objects;
import java.util.UUID;


public class UUIDIndexTest
{
	public static void main(String[] args)
	{
		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();
		
		GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(idIndex);
		
		map.add(new Person(id1, "P1"));
		map.add(new Person(id2, "P2"));
		
		map.query(idIndex.is(id2)).forEach(System.out::println);
	}
	
	
	final static BinaryIndexerUUID<Person> idIndex = new BinaryIndexerUUID.Abstract<>()
	{
		@Override
		protected UUID getUUID(Person entity)
		{
			return entity.id;
		}
	};
	
	static class Person
	{
		final UUID id;
		final String name;
		
		Person(UUID id, String name)
		{
			this.id = id;
			this.name = name;
		}
		
		@Override
		public boolean equals(final Object o)
		{
			if(!(o instanceof Person))
				return false;
			final Person person = (Person)o;
			return Objects.equals(this.id, person.id) && Objects.equals(this.name, person.name);
		}
		
		@Override
		public int hashCode()
		{
			return Objects.hash(this.id, this.name);
		}
		
		@Override
		public String toString()
		{
			return "Person{" +
				"id=" + id + " (" + id.getMostSignificantBits() + ", " + id.getLeastSignificantBits() + ")" +
				", name='" + name + '\'' +
				'}';
		}
	}
	
}
