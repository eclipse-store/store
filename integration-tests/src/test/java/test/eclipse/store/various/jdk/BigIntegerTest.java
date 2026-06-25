package test.eclipse.store.various.jdk;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BigIntegerTest
{
    @TempDir
    Path tempDir;

    @Test
    void bigIntegerStoreAndReload()
    {
        BigInteger bi = new BigInteger("123456789012345678901234567890");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(bi, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            BigInteger loaded = (BigInteger) storageManager.root();

            assertEquals(bi, loaded, "BigInteger should be equal after storing and reloading");
        }
    }

	@Test
	@Disabled("https://github.com/eclipse-store/store/issues/521")
	void bigIntegerVariants()
	{
		BigInteger first = new BigInteger("12");
		try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(first, tempDir)) {
		}

		BigInteger loaded = new BigInteger("123");
		try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
			assertEquals(first,  loaded, "should be equal after storing and reloading");
		}
	}

    @Test
    void saveBigIntegerDataAndReload()
    {
        BigInteger bi = BigInteger.TEN.pow(20);

        BigIntegerData root = new BigIntegerData(bi);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        BigIntegerData loadedRoot = new BigIntegerData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(bi, loadedRoot.getValue(), "BigIntegerData should be equal after storing and reloading");
        }
    }

	private static class BigIntegerData
    {
        private BigInteger value;

        public BigIntegerData(BigInteger value)
        {
            this.value = value;
        }

        public BigIntegerData()
        {
        }

        public BigInteger getValue()
        {
            return value;
        }

        public void setValue(BigInteger value)
        {
            this.value = value;
        }
    }
}

