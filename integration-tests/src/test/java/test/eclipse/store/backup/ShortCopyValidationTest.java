package test.eclipse.store.backup;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.store.storage.exceptions.StorageExceptionIoWriting;
import org.eclipse.store.storage.types.StorageBackupItemEnqueuer;
import org.eclipse.store.storage.types.StorageFileWriter;
import org.eclipse.store.storage.types.StorageFileWriterBackupping;
import org.eclipse.store.storage.types.StorageImportSource;
import org.eclipse.store.storage.types.StorageLiveDataFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Empirical verification of the internal#71 fix (store#744): short copies must fail loudly at
 * every backup-relevant site, and the backupping writer must never enqueue a backup item for a
 * short copy (a wrong-length item would permanently diverge the backup).
 */
@Timeout(60)
public class ShortCopyValidationTest
{
	private static <T> T proxy(final Class<T> type, final InvocationHandler handler)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}

	/** Answers size()=0 and copyTo(...)=shortCount; primitives default otherwise. */
	private static InvocationHandler fileAnsweringShortCopy(final long shortCount)
	{
		return (proxyObj, method, args) ->
		{
			if("copyTo".equals(method.getName()))
			{
				return shortCount;
			}
			if(long.class.equals(method.getReturnType()))
			{
				return 0L;
			}
			if(int.class.equals(method.getReturnType()))
			{
				return 0;
			}
			if(boolean.class.equals(method.getReturnType()))
			{
				return false;
			}
			return null;
		};
	}

	@Test
	void defaultWriteTransferThrowsOnShortCopy()
	{
		final StorageFileWriter defaultWriter = new StorageFileWriter(){};
		final StorageLiveDataFile source = proxy(StorageLiveDataFile.class, fileAnsweringShortCopy(5L));
		final StorageLiveDataFile target = proxy(StorageLiveDataFile.class, fileAnsweringShortCopy(5L));

		final StorageExceptionIoWriting e = assertThrows(StorageExceptionIoWriting.class,
			() -> defaultWriter.writeTransfer(source, 0L, 100L, target),
			"a short transfer copy (5 of 100 bytes) must fail loudly");
		assertTrue(e.getMessage().contains("5") && e.getMessage().contains("100"),
			"the exception must name both byte counts: " + e.getMessage());
	}

	@Test
	void defaultWriteImportThrowsOnShortCopy()
	{
		final StorageFileWriter defaultWriter = new StorageFileWriter(){};
		final StorageImportSource source = proxy(StorageImportSource.class, fileAnsweringShortCopy(7L));
		final StorageLiveDataFile target = proxy(StorageLiveDataFile.class, fileAnsweringShortCopy(7L));

		assertThrows(StorageExceptionIoWriting.class,
			() -> defaultWriter.writeImport(source, 0L, 100L, target),
			"a short import copy must fail loudly");
	}

	@Test
	void backuppingWriterDoesNotEnqueueAnItemForAShortTransfer()
	{
		// delegate that reports a short transfer WITHOUT throwing (custom-writer threat model)
		final StorageFileWriter shortDelegate = new StorageFileWriter()
		{
			@Override
			public long writeTransfer(
				final StorageLiveDataFile sourceFile  ,
				final long                sourceOffset,
				final long                copyLength  ,
				final StorageLiveDataFile targetFile
			)
			{
				return 5L; // short: only 5 of copyLength bytes "copied"
			}
		};

		final List<String> enqueued = new ArrayList<>();
		final StorageBackupItemEnqueuer recordingEnqueuer = proxy(
			StorageBackupItemEnqueuer.class,
			(proxyObj, method, args) -> { enqueued.add(method.getName()); return null; }
		);

		final StorageFileWriter backupping = StorageFileWriterBackupping
			.Provider(recordingEnqueuer, () -> shortDelegate)
			.provideWriter(0);

		final StorageLiveDataFile source = proxy(StorageLiveDataFile.class, fileAnsweringShortCopy(5L));
		final StorageLiveDataFile target = proxy(StorageLiveDataFile.class, fileAnsweringShortCopy(5L));

		assertThrows(StorageExceptionIoWriting.class,
			() -> backupping.writeTransfer(source, 0L, 100L, target),
			"the backupping writer must reject the delegate's short count");
		assertEquals(List.of(), enqueued,
			"no backup item may be enqueued for a short copy - it would permanently diverge the backup");
	}
}
