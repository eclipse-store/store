package org.eclipse.store.examples.gigamap;

import java.util.ArrayList;
import java.util.List;

import com.github.javafaker.Faker;
import org.eclipse.store.gigamap.types.GigaMap;


public class RandomGenerator
{
	public static GigaMap<Person> createMap(final int size)
	{
		final GigaMap<Person> map = GigaMap.<Person>Builder()
			.withBitmapIdentityIndex(PersonIndices.id)
			.withBitmapIndex(PersonIndices.firstName)
			.withBitmapIndex(PersonIndices.lastName)
			.withBitmapIndex(PersonIndices.dateOfBirth)
			.withBitmapIndex(PersonIndices.city)
			.withBitmapIndex(PersonIndices.country)
			.withBitmapIndex(PersonIndices.interests)
			.build();
		
		final List<Person> data  = new ArrayList<>(size);
		final Faker        faker = new Faker();
		for(int i = 1; i <= size; i++)
		{
			data.add(new Person(i, faker));
		}
		map.addAll(data);
		
		return map;
	}
}
