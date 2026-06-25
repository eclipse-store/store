package test.eclipse.store.various;

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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import org.eclipse.serializer.persistence.exceptions.PersistenceException;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class UnloadedLazyTest
{

	@TempDir
	Path location;

	private static PrintStream originalErr;

	@BeforeAll
	static void setupErr()
	{
		originalErr = System.err;
		System.setErr(new PrintStream(new OutputStream()
		{
			@Override
			public void write(int b)
			{
				// Discard output
			}
		}));
	}

	@AfterAll
	static void restoreErr()
	{
		System.setErr(originalErr);
	}


	/**
	 * https://github.com/eclipse-serializer/serializer/issues/221
	 * @param secondLocation
	 */
	@Test
	public void saveDefaultLazySecondTimeTest(@TempDir final Path secondLocation)
	{
		final MyRoot myRoot = new MyRoot("Hello World");

		try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(myRoot, this.location)) {
		}

		myRoot.lazy.clear();

		assertThrows(PersistenceException.class, () -> EmbeddedStorage.start(myRoot, secondLocation));

	}


	public static class MyRoot
	{
		Lazy<String> lazy;
		Integer number = 42;

		public MyRoot(final String content)
		{
			super();
			this.lazy = Lazy.Reference(content);
		}

	}

}
