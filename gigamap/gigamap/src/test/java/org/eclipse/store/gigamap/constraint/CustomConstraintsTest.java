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
import org.eclipse.store.gigamap.types.CustomConstraint;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class CustomConstraintsTest
{
	final static String BAD = "BAD";
	final static String REPLACEMENT = "abcBADdef";
	
	static class NoBadValues extends CustomConstraint.AbstractSimple<Entity>
	{
		@Override
		public boolean isViolated(final Entity entity)
		{
			return entity.getWord().contains(BAD);
		}
	}
	
	
	@Test
	void add()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().ensure(Entity.wordIndex);
		map.constraints().custom().addConstraint(new NoBadValues());

		map.add(Entity.Random().setWord("abc"));
		map.add(Entity.Random().setWord("def"));
		assertThrows(
			ConstraintViolationException.class,
			() -> map.add(Entity.Random().setWord(REPLACEMENT))
		);
	}
	
	@Test
	void set()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().ensure(Entity.wordIndex);
		map.constraints().custom().addConstraint(new NoBadValues());
		
		map.add(Entity.Random().setWord("a"));
		final long id_b = map.add(Entity.Random().setWord("b"));
		
		assertThrows(
			ConstraintViolationException.class,
			() -> map.set(id_b, Entity.Random().setWord(REPLACEMENT))
		);
	}
	
	@Test
	void update()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().ensure(Entity.wordIndex);
		map.constraints().custom().addConstraint(new NoBadValues());
		
		map.add(Entity.Random().setWord("a"));
		final Entity b = Entity.Random().setWord("b");
		map.add(b);
		
		assertThrows(
			ConstraintViolationException.class,
			() -> map.update(b, entity -> entity.setWord(REPLACEMENT))
		);
	}

	@Test
	void customConstraintsWrapperSimple()
	{
		final GigaMap<Entity> map = GigaMap.New();
		final CustomConstraint.WrapperSimple<Entity> constraint = new CustomConstraint.WrapperSimple<>(entity -> entity.getWord().contains("BAD"));
		map.constraints().custom().addConstraint(constraint);

		map.add(Entity.Random().setWord("abc"));
		map.add(Entity.Random().setWord("def"));
		assertThrows(
				ConstraintViolationException.class,
				() -> map.add(Entity.Random().setWord(REPLACEMENT))
		);
	}

	@Test
	void customConstraint_wrapper()
	{
		final GigaMap<Entity> map = GigaMap.New();

		final CustomConstraint.Wrapper<Entity> constraint = new CustomConstraint.Wrapper<>
				((gigaMap, entityId, replacedEntity, entity, createException)
						-> entity.getWord().contains("BAD") ? new ConstraintViolationException(entityId, replacedEntity, entity) : null);
		map.constraints().custom().addConstraint(constraint);

		map.add(Entity.Random().setWord("abc"));
		map.add(Entity.Random().setWord("def"));
		assertThrows(
			ConstraintViolationException.class,
			() -> map.add(Entity.Random().setWord(REPLACEMENT))
		);
	}

	@Test
	void customConstraints_Abstract()
	{
		final GigaMap<Entity> map = GigaMap.New();

		final CustomConstraint.Abstract<Entity> constraint = new CustomConstraint.Abstract<>()
		{
			@Override
			public void check(final long entityId, final Entity replacedEntity, final Entity entity)
			{
				if(entity.getWord().contains("BAD"))
				{
					throw new ConstraintViolationException(entityId, replacedEntity, entity);
				}
			}

		};

		map.constraints().custom().addConstraint(constraint);

		map.add(Entity.Random().setWord("abc"));
		map.add(Entity.Random().setWord("def"));
		assertThrows(
			ConstraintViolationException.class,
			() -> map.add(Entity.Random().setWord(REPLACEMENT))
		);
	}
}
