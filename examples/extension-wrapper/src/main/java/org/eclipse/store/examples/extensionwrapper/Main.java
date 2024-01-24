
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

/**
 * Example which shows how to use the instance dispatching in foundations,
 * in order to extend certain parts of the storage engine.
 *
 */
public class Main
{
	private static List<LocalDateTime> ROOT = new ArrayList<>();
	
	
	public static void main(final String[] args)
	{
		// Create default storage foundation
		final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation();
		
		// Add extender as dispatcher
		foundation.getConnectionFoundation().setInstanceDispatcher(new StorageExtender());
				
		// Start storage
		final EmbeddedStorageManager storage = foundation.start(ROOT);
		
		// See extensions in action
		ROOT.add(LocalDateTime.now());
		storage.storeRoot();
		
		storage.shutdown();
		System.exit(0);
	}
	
}
