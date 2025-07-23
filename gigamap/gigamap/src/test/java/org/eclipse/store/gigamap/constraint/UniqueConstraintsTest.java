package org.eclipse.store.gigamap.constraint;

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

import org.eclipse.store.gigamap.data.Entity;
import org.eclipse.store.gigamap.exceptions.ConstraintViolationException;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UniqueConstraintsTest
{
	@Test
	void add()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().addUniqueConstraint(Entity.uniqueWordIndex);
		
		map.add(Entity.Random().setWord("a"));
		map.add(Entity.Random().setWord("b"));
		
		assertThrows(
			ConstraintViolationException.class,
			() -> map.add(Entity.Random().setWord("b"))
		);
		
		assertThrows(
			ConstraintViolationException.class,
			() -> map.addAll(
				Entity.Random().setWord("a"),
				Entity.Random().setWord("b"),
				Entity.Random().setWord("c")
			)
		);
		
		// constraints violations must rollback all added data
		assertEquals(2, map.size());

		
		// adding different stuff must work properly afterward
		
		map.add(Entity.Random().setWord("c"));
		assertEquals(3, map.size());
		
		map.addAll(
			Entity.Random().setWord("d"),
			Entity.Random().setWord("e"),
			Entity.Random().setWord("f")
		);
		assertEquals(6, map.size());
	}
	
	@Test
	void set()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().addUniqueConstraint(Entity.uniqueWordIndex);
		
		map.add(Entity.Random().setWord("a"));
		final long id_b = map.add(Entity.Random().setWord("b"));
		
		assertThrows(
			ConstraintViolationException.class,
			() -> map.set(id_b, Entity.Random().setWord("a"))
		);
	}
	
	@Test
	void update()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().addUniqueConstraint(Entity.uniqueWordIndex);
		
		map.add(Entity.Random().setWord("a"));
		final Entity b = Entity.Random().setWord("b");
		map.add(b);
		
		assertThrows(
			ConstraintViolationException.class,
			() -> map.update(b, entity -> entity.setWord("a"))
		);
	}
}
