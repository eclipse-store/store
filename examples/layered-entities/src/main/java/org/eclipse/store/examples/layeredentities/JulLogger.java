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

import java.util.logging.Logger;

import org.eclipse.serializer.entity.Entity;
import org.eclipse.serializer.entity.EntityLogger;

public class JulLogger implements EntityLogger
{
	public JulLogger()
	{
		super();
	}
	
	@Override
	public void entityCreated(final Entity identity, final Entity data)
	{
		Logger.getLogger(identity.getClass().getName())
		.info("Entity created: " +  data);
	}
	
	@Override
	public void afterUpdate(
		final Entity identity,
		final Entity data,
		final boolean successful
	)
	{
		Logger.getLogger(identity.getClass().getName())
			.info("Entity updated: " + data);
	}
}
