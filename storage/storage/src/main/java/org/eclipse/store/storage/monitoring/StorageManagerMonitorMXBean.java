package org.eclipse.store.storage.monitoring;

import org.eclipse.serializer.monitoring.MonitorDescription;

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

import org.eclipse.store.storage.monitoring.StorageManagerMonitor.StorageStatistics;
import org.eclipse.store.storage.types.StorageManager;

/**
 * JMX MBean definition that provides monitoring and metrics for
 * a {@link StorageManager} instance.
 */
@MonitorDescription("Provides storage statistics and house keeping operations")
public interface StorageManagerMonitorMXBean
{
	/**
	 * Get storage statistics.
	 * 
	 * @return storage statistics.
	 */
	@MonitorDescription("query the storage for storage statistics. "
			+ "This will block storage operations until it is completed.")
	StorageStatistics getStorageStatistics();
	
	/**
	 * Issue a full storage garbage collection.
	 */
	@MonitorDescription("issue a full storage garbage collection run. "
			+ "This will block storage operations until it is completed.")
	
	void issueFullGarbageCollection();

	/**
	 * Issue a full storage file check.
	 */
	@MonitorDescription("issue a full storage file check run. "
			+ "This will block storage operations until it is completed.")
	void issueFullFileCheck();

	/**
	 * Issue a full storage cache check.
	 */
	@MonitorDescription("issue a full storage chache check run. "
			+ "This will block storage operations until it is completed.")
	void issueFullCacheCheck();
}
