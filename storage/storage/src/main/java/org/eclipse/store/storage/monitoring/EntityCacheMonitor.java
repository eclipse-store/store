package org.eclipse.store.storage.monitoring;

import java.lang.ref.WeakReference;

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

import org.eclipse.serializer.monitoring.MetricMonitor;
import org.eclipse.store.storage.types.StorageEntityCache;

public class EntityCacheMonitor implements EntityCacheMonitorMBean, MetricMonitor
{
	private final WeakReference<StorageEntityCache.Default> storageEntityCache;
	private int channelIndex;
	
	public EntityCacheMonitor(final StorageEntityCache.Default storageEntityCache)
	{
		super();
		this.storageEntityCache = new WeakReference<>(storageEntityCache);
		this.channelIndex = storageEntityCache.channelIndex();
	}

	@Override
	public int getChannelIndex()
	{
		return this.channelIndex;
	}

	@Override
	public long getLastSweepStart()
	{
		return this.storageEntityCache.get().lastSweepStart();
	}

	@Override
	public long getLastSweepEnd()
	{
		return this.storageEntityCache.get().lastSweepEnd();
	}

	@Override
	public long getEntityCount()
	{
		return this.storageEntityCache.get().entityCount();
	}

	@Override
	public long getUsedCacheSize()
	{
		return this.storageEntityCache.get().cacheSize();
	}

	@Override
	public String getName()
	{
		return "channel=channel-"
			+ this.channelIndex
			+ ",group=Entity cache";
	}

}
