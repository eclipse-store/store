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

import org.eclipse.serializer.functional.InstanceDispatcherLogic;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceStorer;
import org.eclipse.serializer.persistence.types.PersistenceTarget;

/**
 * Dispatcher logic which is used to extend certain parts
 */
public class StorageExtender implements InstanceDispatcherLogic
{
	@SuppressWarnings("unchecked")
	@Override
	public <T> T apply(final T subject)
	{
		if(subject instanceof PersistenceTarget)
		{
			return (T)new PersistenceTargetExtension((PersistenceTarget<Binary>)subject);
		}
		
		if(subject instanceof PersistenceStorer.Creator)
		{
			return (T)new PersistenceStorerExtension.Creator((PersistenceStorer.Creator<Binary>)subject);
		}
		
		return subject;
	}
}
