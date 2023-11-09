package org.eclipse.store.examples.eagerstoring;

/*-
 * #%L
 * EclipseStore Example Eager Storing
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

import java.lang.reflect.Field;

import org.eclipse.serializer.persistence.types.PersistenceEagerStoringFieldEvaluator;

/**
 * Custom field evaluator which looks for the {@link StoreEager} annotation.
 *
 */
public class StoreEagerEvaluator implements PersistenceEagerStoringFieldEvaluator
{

	@Override
	public boolean isEagerStoring(
		final Class<?> clazz,
		final Field    field
	)
	{
		return field.isAnnotationPresent(StoreEager.class);
	}
	
}
