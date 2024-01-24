package org.eclipse.store.examples.layeredentities;

/*-
 * #%L
 * EclipseStore Example Layered Entities
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.collections.types.XGettingTable;
import org.eclipse.serializer.entity.Entity;
import org.eclipse.serializer.entity.EntityVersionContext;
import org.eclipse.store.examples.layeredentities._Human.HumanUpdater;

public class LayeredEntities
{
	public static void main(final String[] args)
	{
		final Human human = EntityFactory.HumanCreator()
			.name("John Doe")
			.address(
				EntityFactory.AddressCreator()
					.street("Main Street")
					.city("Springfield")
					.create()
			)
			.create();
		
		HumanUpdater.setAddress(
			human,
			EntityFactory.AddressCreator()
				.street("Rose Boulevard")
				.city("Newtown")
				.create()
		);
		
		printVersions(human);
	}

	static void printVersions(final Entity entity)
	{
		final EntityVersionContext<Integer>  context  = EntityVersionContext.lookup(entity);
		final XGettingTable<Integer, Entity> versions = context.versions(entity);
		versions.iterate(v ->
			System.out.println("Version " + v.key() + " = " + v.value())
		);
	}
	
}
