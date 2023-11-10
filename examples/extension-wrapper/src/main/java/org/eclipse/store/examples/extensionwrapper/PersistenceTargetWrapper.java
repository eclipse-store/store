package org.eclipse.store.examples.extensionwrapper;

/*-
 * #%L
 * EclipseStore Example Extension Wrapper
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

import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.exceptions.PersistenceExceptionTransfer;
import org.eclipse.serializer.persistence.types.PersistenceTarget;

/**
 * Wrapper for {@link PersistenceTarget}, used as base for extensions
 *
 */
public class PersistenceTargetWrapper implements PersistenceTarget<Binary>
{
	private final PersistenceTarget<Binary> delegate;

	public PersistenceTargetWrapper(final PersistenceTarget<Binary> delegate)
	{
		super();
		this.delegate = delegate;
	}

	@Override
	public boolean isWritable()
	{
		return this.delegate.isWritable();
	}

	@Override
	public void write(final Binary data) throws PersistenceExceptionTransfer
	{
		this.delegate.write(data);
	}
	
	
}
