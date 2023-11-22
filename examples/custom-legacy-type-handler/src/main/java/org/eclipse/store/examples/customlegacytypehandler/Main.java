package org.eclipse.store.examples.customlegacytypehandler;

/*-
 * #%L
 * EclipseStore Example Legacy Type Handler
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

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

/**
 * 
 * This example shows how custom legacy type mapping is done.
 * 
 * USAGE:
 * 
 * Run this sample twice. For the first run use the upper first generation "NicePlace" Object to setup the initial storage.
 * Before the second run switch to the modified, second generation version of the "NicePlace" object by
 * commenting out the initial version and using the modified one in NicePlace.java.
 *
 * the custom type mapping is done by the LegacyTypeHandlerNicePlace.
 *
 */
public class Main
{
	static final File workingdir = GetTempWorkDirectory("e008-LegacyTypeMappingExample");
	static final String storageChannelFileName = "channel_0\\channel_0_1.dat";
	
	@SuppressWarnings("resource")
	public static void main(final String[] args)
	{
		final EmbeddedStorageManager storage;
				
		//if no channel storage files are found a new storage is created
		if(!Files.exists(Paths.get(workingdir.getPath(), storageChannelFileName)))
		{
			storage = EmbeddedStorage.Foundation(workingdir.toPath()).start();
			final NicePlace myPlace = new NicePlace("Campground", "not far away");
			storage.setRoot(myPlace);
			storage.storeRoot();
						
			System.out.println("created storage with legacy object:");
			System.out.println(ObjectToString(myPlace));
			System.out.println(myPlace + "\n");
		}
		else
		{
			//a channel storage file is found, try to load the storage and use
			//the custom legacy type handler "NicePlaceLegacyHandler"
			
			storage = EmbeddedStorage.Foundation(workingdir.toPath())
					.onConnectionFoundation(f ->
						f.getCustomTypeHandlerRegistry()
							.registerLegacyTypeHandler(new LegacyTypeHandlerNicePlace()))
					.start();
			
			final NicePlace myPlace = (NicePlace)storage.root();
	
			System.out.println("loaded legacy storage:");
			System.out.println(ObjectToString(myPlace));
			System.out.println(myPlace + "\n");
		}
		
		storage.shutdown();
	}
	
	/**
	 * Object class and field to String
	 * 
	 * @param o Object
	 * @return String
	 */
	public static String ObjectToString(final Object o)
	{
		final StringBuilder str = new StringBuilder();
		
		final Class<?> T = o.getClass();
		
		str.append(T.getName() + "\n");
		
		for (final Field field : T.getDeclaredFields())
		{
			str.append(field + "\n");
		}
		
		return str.toString();
	}
	
	/**
	 * create a working directory with constant name in system's temporary directory
	 */
	public static File GetTempWorkDirectory(final String name)
	{
		final File tmpPath = new File(System.getProperty("java.io.tmpdir"));
		if(tmpPath.canWrite() && tmpPath.isDirectory())
		{
			final File p = Paths.get(tmpPath.toString(), name).toFile();
			if(p.exists())
			{
				return p;
			}
			if(p.mkdir())
			{
				return p;
			}
		}
				
		throw new RuntimeException("Faild to get or create working directory!");
	}
}
