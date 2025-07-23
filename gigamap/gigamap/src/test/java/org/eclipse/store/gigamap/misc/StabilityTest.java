
package org.eclipse.store.gigamap.misc;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerLocalDate;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;


public class StabilityTest
{
	@TempDir
	Path tempDir;
	
	@Test
	void basicStabilityTest()
	{
		final GigaMap<Data> gigaMap = GigaMap.New();
		gigaMap.index().bitmap()
			.add(new DateIndexer());
		
		final EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, this.tempDir);
		manager.storeRoot();
		
		for(int i = 0; i < 1000; i++)
		{
			gigaMap.add(new Data(LocalDate.now()));
		}
		gigaMap.store();
		manager.shutdown();
	}
	
	static class DateIndexer extends IndexerLocalDate.Abstract<Data>
	{
		@Override
		protected LocalDate getLocalDate(final Data entity)
		{
			return entity.date;
		}
	}
	
	static class Data
	{
		final LocalDate date;
		
		public Data(final LocalDate date)
		{
			this.date = date;
		}
		
	}
}
