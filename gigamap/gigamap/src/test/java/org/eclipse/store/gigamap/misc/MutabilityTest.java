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

import org.eclipse.serializer.concurrency.XThreads;
import org.eclipse.serializer.math.XMath;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MutabilityTest
{
	@Test
	void waitForMutability()
	{
		final GigaMap<String> map = GigaMap.New();
		map.add("A0");
		
		// printer thread that takes a long time to print the map to the console
		new Thread("prnt")
		{
			@Override
			public void run()
			{
				while(map.size() < 10)
				{
					for(final String s : map)
					{
						XThreads.sleep(1000 / map.size());
//						System.out.println("Thread " + Thread.currentThread().getName() + " printing: " + s);
					}
//					System.out.println();
					XThreads.sleep(XMath.random(3));
				}
			}
		}.start();
		
		// main thread tries to add new entities but has to constantly wait for the lazy a** printer thread.
		for(int i = 1; i < 10; i++)
		{
//			System.err.println("Thread " + Thread.currentThread().getName() + " adding: B" + i);
			map.add("A" + i);
			XThreads.sleep(1);
		}
		
		assertEquals(10, map.size());
	}
}
