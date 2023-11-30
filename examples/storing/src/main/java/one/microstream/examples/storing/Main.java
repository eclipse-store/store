package one.microstream.examples.storing;

/*-
 * #%L
 * EclipseStore Example Storing
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
 * Microstream data storing example
 *
 */
public class Main
{
    public static void main( final String[] args )
    {
    	// Root instance
    	final MyRoot root = new MyRoot();

    	// Init storage manager
    	final EmbeddedStorageManager storageManager = EmbeddedStorage.start(root);
    
    	//store the root object
    	storageManager.storeRoot();
    	
    	//add a new data object to the list in root
    	final MyData dataItem  = new MyData("Alice");
    	root.myObjects.add(dataItem);
    	
    	//store the modified list
    	storageManager.store(root.myObjects);
    	
    	//modify a value type member and store it
    	dataItem .setIntValue(100);
    	storageManager.store(dataItem);
    	
    	//modify a string object and store it
    	dataItem .setName("Bob");
    	storageManager.store(dataItem);
    	    	
    	
    	//shutdown
    	storageManager.shutdown();
    }
}
