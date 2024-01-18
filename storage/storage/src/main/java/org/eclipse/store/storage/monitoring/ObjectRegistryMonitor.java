package org.eclipse.store.storage.monitoring;

/*-
 * #%L
 * EclipseStore Storage
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

import java.lang.ref.WeakReference;

import org.eclipse.serializer.monitoring.MetricMonitor;
import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;

public class ObjectRegistryMonitor implements ObjectRegistryMonitorMBean, MetricMonitor
{
	WeakReference<PersistenceObjectRegistry> persistenceObjectRegistry;
	
	public ObjectRegistryMonitor(final PersistenceObjectRegistry persistenceObjectRegistry)
	{
		super();
		this.persistenceObjectRegistry = new WeakReference<>(persistenceObjectRegistry);
	}
	
	@Override
	public long getCapacity()
	{
		return this.persistenceObjectRegistry.get().capacity();
	}
	
	@Override
	public long getSize()
	{
		return this.persistenceObjectRegistry.get().size();
	}
	
	@Override
	public String getName()
	{
		return "name=ObjectRegistry";
	}

}
