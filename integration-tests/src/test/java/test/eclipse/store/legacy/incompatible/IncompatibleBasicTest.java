package test.eclipse.store.legacy.incompatible;

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

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.exceptions.TypeCastException;
import org.eclipse.serializer.persistence.types.PersistenceRefactoringMappingProvider;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.legacy.legacy.incompatible.data.IncompPerson;
import test.eclipse.store.legacy.legacy.incompatible.data.IncompPerson2;

class IncompatibleBasicTest
{

	@TempDir
	Path tempDir;

	private EmbeddedStorageManager storage;

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

	@AfterEach
	void cleanStorage()
	{
		if (null != storage && !storage.isShutdown()) {
			storage.shutdown();
		}
	}

	@Test
	void incompatibilityTest()
	{
		IncompPerson person = new IncompPerson("Karel", "May", "1001");

		storage = EmbeddedStorage.start(person, tempDir);
		storage.shutdown();

		IncompPerson2 person2 = new IncompPerson2();

		storage = EmbeddedStorage.Foundation(tempDir).setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(EqHashTable.New(KeyValue.New("test.eclipse.store.legacy.legacy.incompatible.data.Person", "test.eclipse.store.legacy.legacy.incompatible.data.Person2")))).setRoot(person2).createEmbeddedStorageManager();

		Assertions.assertThrows(TypeCastException.class, () -> storage.start());
	}

}
