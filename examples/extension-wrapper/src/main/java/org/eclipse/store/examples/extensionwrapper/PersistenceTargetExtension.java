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
 * Extension for {@link PersistenceTarget} which adds logic to write operations
 *
 */
public class PersistenceTargetExtension extends PersistenceTargetWrapper
{
	public PersistenceTargetExtension(final PersistenceTarget<Binary> delegate)
	{
		super(delegate);
	}


	@Override
	public void write(final Binary data) throws PersistenceExceptionTransfer
	{
		// Original write
		super.write(data);
		
		// Add extension code
		System.out.println("Data written");
	}
	
	
}
