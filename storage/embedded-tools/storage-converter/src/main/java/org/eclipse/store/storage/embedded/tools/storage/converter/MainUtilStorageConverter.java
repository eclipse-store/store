package org.eclipse.store.storage.embedded.tools.storage.converter;

/*-
 * #%L
 * EclipseStore Storage Embedded Tools Storage Converter
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.eclipse.store.storage.types.StorageConfiguration;

/**
 * Utility class that allows to convert a storage into another
 * one. The current implementation can change channel count only
 * and is limited to the systems default NioFileSystem
 * because of the missing possibility to define the StorageFileSystem
 * in configuration files.
 * If the file system has to be changed the {@link StorageConverter} class
 * may be used in a custom converter implementation.
 * Existing backups will not be converted!
 *
 */
public class MainUtilStorageConverter
{
	public static String HELP =
		"Convert a storage into a new one. The source and the new target storage \n"
		+ "must be specified in storage config files provided as program arguments: \n"
		+ "\n"
		+ "MainUtilStorageConverter sourceConfig.ini targetConfig.ini"
		+ "\n"
		+ "Optional: provide a list of BinaryConverter implementations as ONE sting include the -c:"
		+ " \n"
		+ "\"-c binaryConverter1, binaryConverter2\""
		+ "It is import to have the \"-c\" inside the quotation marks, "
		+ "the converters must be specified using the full class name"
		;
					
	public static void main(final String[] args)
	{
		verifyArguments(args);
		
		final String srcConfigFile = args[0];
		final String dstConfigFile = args[1];
		
		final StorageConfiguration sourceConfig = EmbeddedStorageConfiguration.load(srcConfigFile)
			.createEmbeddedStorageFoundation().getConfiguration();
		
		final StorageConfiguration targetConfig = EmbeddedStorageConfiguration.load(dstConfigFile)
			.createEmbeddedStorageFoundation().getConfiguration();
		
		String[] binaryConverters = {};
		if(args.length > 2)
		{
			for(int i = 2; i < args.length; i++)
			{
				if(args[i].startsWith("-c"))
				{
					binaryConverters = parseBinaryConverters(args, i);
				}
			}
		}
		
		System.out.println("Source storage configuration: " + srcConfigFile);
		System.out.println("Target storage configuration: " + dstConfigFile);
		System.out.println("Binary format converters: " + Arrays.toString(binaryConverters));
		
		final StorageConverter storageConverter = new StorageConverter(sourceConfig, targetConfig, binaryConverters);
		storageConverter.start();
		
		System.out.println("Storage conversion finished!");
	}

	private static String[] parseBinaryConverters(String[] args, int startIndex)
	{
		List<String> binaryConverters = new ArrayList<>();
		
		for(int i = startIndex + 1; i < args.length; i++)
		{
			if(args[i].startsWith("-"))
			{
				return binaryConverters.toArray(new String[0]);
			}
			else
			{
				binaryConverters.add(args[i]);
			}
		}
		return binaryConverters.toArray(new String[0]);
	}

	private static void verifyArguments(final String[] args)
	{
		if(args.length >= 2)
		{
			if(new File(args[0]).canRead())
			{
				if(new File(args[1]).canRead())
				{
					return;
				}
				else
				{
					System.err.println("Can't read file " + args[1]);
				}
			}
			else
			{
				System.err.println("Can't read file " + args[0]);
			}
		}
		else
		{
			System.err.println("Invalid number of arguments.");
		}
		
		System.out.println(HELP);
		System.exit(-1);
	}
}
