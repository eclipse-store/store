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

import org.eclipse.serializer.monitoring.MonitorDescription;
import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;

/**
 * JMX MBean definition that provides monitoring and metrics for
 * a {@link PersistenceObjectRegistry} instance.
 */
@MonitorDescription("Provides object registry related data.")
public interface ObjectRegistryMonitorMBean
{
	/**
	 * Get the number of registered objects (size of object registry).
	 * 
	 * @return the number of registered objects.
	 */
	@MonitorDescription("The number of currently registered objects.")
	long getSize();

	/**
	 * Get the reserved size (number of objects) of the object registry.
	 * 
	 * @return reserved size (as number of objects).
	 */
	@MonitorDescription("The reserved size(number of objects) of the object registry.")
	long getCapacity();
}
