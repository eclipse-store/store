package test.eclipse.store.collections.lazy.arraylist;

/*-
 * #%L
 * EclipseStore Integration Tests
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class Utils {

	public static void deleteAll(final Path path) throws IOException {
		if(path.toFile().exists()) {
			Files.walk(path)
				.sorted(Comparator.reverseOrder())
			 	.map(Path::toFile)
			 	.forEach(File::delete);
		}
	}
	
	public static void deleteAll(final String path) throws IOException {
		deleteAll(Paths.get(path));
	}

	public static void pressAnyKeyToContinue()
	{
		System.out.println("Press Enter key to continue...");
		try {
			System.in.read();
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static void pressAnyKeyToContinue(final String string)
	{
		System.out.println(string + " Press Enter key to continue...");
		try {
			System.in.read();
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

}
