package org.eclipse.store.storage.monitoring;

import org.eclipse.serializer.monitoring.MonitorDescription;
import org.eclipse.store.storage.types.StorageEntityCache;

/*-
 * #%L
 * EclipseStore Storage
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

/**
 * JMX MBean definition that provides a summary of monitoring and metrics for
 * all {@link StorageEntityCache} instances of a storage.
 */
@MonitorDescription("Provides a summary of all storage channels entity caches.")
public interface EntityCacheSummaryMonitorMBean
{
	/**
	 * Get the aggregated used cache size from all channel entity caches in bytes.
	 * 
	 * @return used cache size in bytes.
	 */
	@MonitorDescription("The total size of all channel entity caches in bytes.")
	long getUsedCacheSize();

	/**
	 * The number of entries aggregated from all channel entity caches.
	 * 
	 * @return The number of entries.
	 */
	@MonitorDescription("The number of entries aggregated from all channel entity caches.")
	long getEntityCount();
}
