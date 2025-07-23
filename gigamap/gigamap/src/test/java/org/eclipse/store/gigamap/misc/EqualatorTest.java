package org.eclipse.store.gigamap.misc;

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

import org.eclipse.serializer.equality.Equalator;
import org.eclipse.serializer.hashing.XHashing;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EqualatorTest
{
	@Test
	void identityMatch()
	{
		final GigaMap<String> gigaMap = newMap(XHashing.hashEqualityIdentity());
		
		gigaMap.add(sameString());
		assertEquals(0, gigaMap.remove(sameString()));
	}
	
	@Test
	void identityNoMatch()
	{
		final GigaMap<String> gigaMap = newMap(XHashing.hashEqualityIdentity());
		
		gigaMap.add(newString());
		assertEquals(-1, gigaMap.remove(newString()));
	}
	
	@Test
	void equalityMatch()
	{
		final GigaMap<String> gigaMap = newMap(XHashing.hashEqualityValue());
		
		gigaMap.add(sameString());
		assertEquals(0, gigaMap.remove(sameString()));
	}
	
	@Test
	void equalityMatch2()
	{
		final GigaMap<String> gigaMap = newMap(XHashing.hashEqualityValue());
		
		gigaMap.add(newString());
		assertEquals(0, gigaMap.remove(newString()));
	}
	
	
	private static GigaMap<String> newMap(final Equalator<String> equalator)
	{
		final GigaMap<String> gigaMap = GigaMap.New(equalator);
		gigaMap.index().bitmap().add(new IndexerString.Abstract<String>()
		{
			@Override
			protected String getString(final String entity)
			{
				return entity;
			}
		});
		return gigaMap;
	}
	
	
	private static String sameString()
	{
		return "1000";
	}

	private static String newString()
	{
		return String.valueOf(1000);
	}
	
}
