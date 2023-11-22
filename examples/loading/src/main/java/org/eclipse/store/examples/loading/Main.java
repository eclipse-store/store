package org.eclipse.store.examples.loading;

/*-
 * #%L
 * EclipseStore Example Loading
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

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;


/**
 * 
 * A very simple example how to load an existing database
 * Run twice, the first run creates a database, all further runs
 * load it.
 *
 */
public class Main
{
	public static void main(final String[] args)
	{
    	// Init storage manager
    	final EmbeddedStorageManager storage = EmbeddedStorage.start();
    	    	
    	//if storage.root() returns null no data have been loaded
    	//since there is no existing database, let's create a new one.
    	if(storage.root() == null)
    	{
    		System.out.println("No existing Database found, creating a new one:");
    		
    		final MyRoot root = new MyRoot();
    		storage.setRoot(root);
    		root.myObjects.add(new MyData("Alice", 20));
    		root.myObjects.add(new MyData("Bob"  , 25));
    		storage.storeRoot();
    	}
    	//storage.root() is not null so we have loaded data
    	else
    	{
    		System.out.println("Existing Database found:");
    		
    		final MyRoot root = (MyRoot) storage.root();
    		root.myObjects.forEach(System.out::println);
    	}
    	
    	storage.shutdown();
	}
}
