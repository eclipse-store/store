package org.eclipse.store.examples.gigamap;

/*-
 * #%L
 * EclipseStore Example GigaMap
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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.store.gigamap.types.GigaMap;

import net.datafaker.Faker;


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
		final Faker faker = new Faker();
		for(int i = 1; i <= size; i++)
		{
			data.add(new Person(i, faker));
		}
		map.addAll(data);
		
		return map;
	}
}
