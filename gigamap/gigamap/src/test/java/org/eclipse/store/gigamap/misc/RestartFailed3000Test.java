
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
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class RestartFailed3000Test
{
	
	final static int AMOUNT = 1_000;
	
	@TempDir
	Path             newDirectory;
	
	@Test
	void restart_test_with_3000()
	{
		final GigaMap<String> gigaMap = GigaMap.New();
		
		try(EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, this.newDirectory))
		{
			
			for(int i = 0; i < AMOUNT; i++)
			{
				gigaMap.add("Hello" + i);
			}
			gigaMap.store();
			
			for(int i = 0; i < AMOUNT; i++)
			{
				gigaMap.add("ahoj" + i);
			}
			gigaMap.store();
			
			for(int i = 0; i < AMOUNT; i++)
			{
				gigaMap.add("servus" + i);
			}
			gigaMap.store();
			assertEquals(3000, gigaMap.size());
			
			assertEquals("servus0", gigaMap.get(2000));
		}
		
		try(EmbeddedStorageManager manager = EmbeddedStorage.start(this.newDirectory))
		{
			final GigaMap<String> loadedMap = (GigaMap<String>)manager.root();
			assertEquals(3000, loadedMap.size());
			assertEquals(AMOUNT * 3, loadedMap.size());
			assertEquals("Hello0", loadedMap.get(0));
			assertEquals("ahoj0", loadedMap.get(1000));
			assertEquals("servus0", loadedMap.get(2000));
		}
	}
}
