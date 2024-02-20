
package org.eclipse.store.integrations.cdi.exceptions;

/*-
 * #%L
 * EclipseStore Integrations CDI 4
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

public class CDIExceptionStorage extends CDIException
{
	private static final String MESSAGE = "There is an incompatibility between the entity and the"
		+ " current root in the StorageManager. Please check the compatibility. "
		+ "Entity: %s and current root class %s";
	
	
	
	public <T, E> CDIExceptionStorage(final Class<T> entity, final Class<E> root)
	{
		super(String.format(MESSAGE, entity, root));
	}
}
