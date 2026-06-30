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
import org.eclipse.store.storage.types.StorageChunkChecksumPolicy;
import org.eclipse.store.storage.types.StorageChunkChecksumProvider;
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
	/**
	 * Help text printed to standard output when the program is invoked with insufficient or unreadable
	 * arguments.
	 */
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
		+ "\n\n"
		+ "Verify-only: provide a single source config (no target) to scan and verify the source\n"
		+ "storage's chunk checksums without writing anything: \n"
		+ "\n"
		+ "MainUtilStorageConverter sourceConfig.ini"
		;

	/**
	 * Command-line entry point of the storage converter utility.
	 * <p>
	 * Expected arguments (in order):
	 * <ol>
	 *   <li>path to the source storage configuration file (INI/XML/properties),</li>
	 *   <li>path to the target storage configuration file,</li>
	 *   <li>optional {@code -c} switch followed by one or more fully qualified
	 *       {@link BinaryConverter} class names that should be applied during conversion.</li>
	 * </ol>
	 * On invalid arguments, {@link #HELP} is printed and the JVM is terminated with a non-zero exit code.
	 *
	 * @param args the command-line arguments.
	 */
	public static void main(final String[] args)
	{
		verifyArguments(args);

		final String srcConfigFile = args[0];
		final StorageConfiguration sourceConfig = loadConfiguration(srcConfigFile);

		// Verify-only mode: a single source config, no target — scan and verify, write nothing.
		if(args.length == 1)
		{
			System.out.println("Source storage configuration: " + srcConfigFile);
			System.out.println("Source checksum policy: " + describe(sourceConfig.chunkChecksumProvider()));
			System.out.println("Verify-only mode: no target configured; scanning source, writing nothing.");
			try
			{
				new StorageConverter(sourceConfig).start();
				System.out.println("Source verification completed (no fatal anomaly).");
			}
			catch(final Throwable t)
			{
				System.err.println("Source verification FAILED: " + t);
				System.exit(1);
			}
			return;
		}

		final String dstConfigFile = args[1];
		final StorageConfiguration targetConfig = loadConfiguration(dstConfigFile);

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
		System.out.println("Source checksum policy: " + describe(sourceConfig.chunkChecksumProvider()));
		System.out.println("Target checksum policy: " + describe(targetConfig.chunkChecksumProvider()));
		System.out.println("Binary format converters: " + Arrays.toString(binaryConverters));

		final StorageConverter storageConverter = new StorageConverter(sourceConfig, targetConfig, binaryConverters);
		storageConverter.start();

		System.out.println("Storage conversion finished!");
	}

	private static StorageConfiguration loadConfiguration(final String configFile)
	{
		return EmbeddedStorageConfiguration.load(configFile)
			.createEmbeddedStorageFoundation().getConfiguration();
	}

	/**
	 * Human-readable summary of a checksum provider's effective behavior. Note: the external configuration
	 * format has no checksum-provider key, so a config-file-driven run reflects the framework default.
	 */
	private static String describe(final StorageChunkChecksumProvider provider)
	{
		final StorageChunkChecksumPolicy policy = provider.policy();
		if(!policy.emit() && !policy.verify())
		{
			return "off (no emit, no verify)";
		}
		final StringBuilder sb = new StringBuilder();
		sb.append("emit=").append(policy.emit()).append(", verify=").append(policy.verify());
		if(policy.emit())
		{
			sb.append(", writeKind=0x").append(Long.toHexString(provider.chunkChecksumKind()));
		}
		return sb.toString();
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
		if(args.length >= 1)
		{
			if(new File(args[0]).canRead())
			{
				if(args.length == 1)
				{
					return; // verify-only: source config alone
				}
				if(new File(args[1]).canRead())
				{
					return; // conversion: source + target config
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
