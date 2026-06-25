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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageWriteControllerReadOnlyMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.serializer.fixtures.TypeRegister;

public class ReadOnlyTest
{
	@TempDir
	Path workDir;

	@Test
	void readOnlyTest() throws IOException, NoSuchAlgorithmException, InterruptedException
	{
		TypeRegister register = new TypeRegister();
		register.fillSampleDate();

		List<TypeRegister> typeRegisters = new ArrayList<>();

		for (int i = 0; i < 5; i++) {
			TypeRegister register1 = new TypeRegister();
			register1.fillSampleDate();
			typeRegisters.add(register1);
		}

		try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(typeRegisters, workDir)) {
			typeRegisters.clear();
			storageManager.storeRoot();
		}

		Map<String, String> checksums = FastChecksumCalculator.calculateAttributes(workDir);

		EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(workDir);
		final StorageWriteControllerReadOnlyMode storageWriteController =
				new StorageWriteControllerReadOnlyMode(foundation.getWriteController());
		storageWriteController.setReadOnly(true);
		foundation.setWriteController(storageWriteController);

		List<TypeRegister> newRegister = new ArrayList<>();

		try (EmbeddedStorageManager readOnlyStoreManager = foundation.setRoot(newRegister).createEmbeddedStorageManager()) {
		}

		Map<String, String> checksums2 = FastChecksumCalculator.calculateAttributes(workDir);
		Assertions.assertEquals(checksums, checksums2);

	}

	@Test
	void simpleReadOnly_isRunning()
	{

		String data = "akfdjlsjfkdwjljd";

		try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(data, workDir)) {
		}

		EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(workDir);
		final StorageWriteControllerReadOnlyMode storageWriteController =
				new StorageWriteControllerReadOnlyMode(foundation.getWriteController());
		storageWriteController.setReadOnly(true);
		foundation.setWriteController(storageWriteController);

		try (EmbeddedStorageManager readOnlyStoreManager = foundation.setRoot(data).createEmbeddedStorageManager().start()) {
			Assertions.assertTrue(readOnlyStoreManager.isRunning());
		}
	}


// java


	private static class FastChecksumCalculator
	{

		// Fastest check: only size + last modification
		public static Map<String, String> calculateAttributes(Path directoryPath) throws IOException
		{
			Objects.requireNonNull(directoryPath);
			try (Stream<Path> paths = Files.walk(directoryPath)) {
				return paths.filter(Files::isRegularFile)
						.collect(Collectors.toMap(
								Path::toString,
								p -> {
									try {
										long size = Files.size(p);
										FileTime ft = Files.getLastModifiedTime(p);
										return size + "-" + ft.toMillis();
									} catch (IOException e) {
										throw new UncheckedIOException(e);
									}
								}
						));
			} catch (UncheckedIOException uio) {
				throw uio.getCause();
			}
		}

		// Fast CRC32 checksum (file reading) with parallel processing
		public static Map<String, String> calculateCrc32Parallel(Path directoryPath) throws IOException
		{
			Objects.requireNonNull(directoryPath);
			Map<String, String> result = new ConcurrentHashMap<>();
			try (Stream<Path> paths = Files.walk(directoryPath)) {
				paths.parallel()
						.filter(Files::isRegularFile)
						.forEach(p -> {
							try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(p))) {
								CRC32 crc = new CRC32();
								byte[] buf = new byte[8 * 1024];
								int r;
								while ((r = in.read(buf)) != -1) {
									crc.update(buf, 0, r);
								}
								result.put(p.toString(), Long.toHexString(crc.getValue()));
							} catch (IOException e) {
								throw new UncheckedIOException(e);
							}
						});
				return result;
			} catch (UncheckedIOException uio) {
				throw uio.getCause();
			}
		}
	}


	private static class ChecksumCalculator
	{

		public static Map<String, String> calculateChecksums(Path directoryPath) throws IOException, NoSuchAlgorithmException
		{
			Map<String, String> checksums = new HashMap<>();
			try (Stream<Path> paths = Files.walk(directoryPath)) {
				paths.filter(Files::isRegularFile).forEach(path -> {
					try {
						checksums.put(path.toString(), calculateChecksum(path));
					} catch (IOException | NoSuchAlgorithmException e) {
						e.printStackTrace();
					}
				});
			}
			return checksums;
		}

		private static String calculateChecksum(Path filePath) throws IOException, NoSuchAlgorithmException
		{
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			try (DigestInputStream dis = new DigestInputStream(new FileInputStream(filePath.toFile()), md)) {
				while (dis.read() != -1) ; //empty loop to clear the data
				md = dis.getMessageDigest();
			}

			// bytes to hex
			StringBuilder result = new StringBuilder();
			for (byte b : md.digest()) {
				result.append(String.format("%02x", b));
			}
			return result.toString();
		}
	}
}
