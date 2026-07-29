package test.eclipse.store.recovery;

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

import org.eclipse.serializer.memory.XMemory;
import org.eclipse.store.storage.types.StorageTransactionsAnalysis.Logic;
import org.eclipse.store.storage.types.StorageTransactionsEntryType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Byte layout of the transactions-log entries. Recovery re-reads these entries and the compactor
 * re-writes them, so their fixed lengths and field offsets must stay stable; a drift silently
 * corrupts every log this build writes or reads.
 * <p>
 * Regression guard: the entry layout is the same before and after this change; this only locks it against regressions.
 */
class TransactionsLogEntryFormatTest
{
	private java.nio.ByteBuffer buffer ;
	private long                address;

	@BeforeEach
	void allocate()
	{
		this.buffer  = XMemory.allocateDirectNative(64);
		this.address = XMemory.getDirectByteBufferAddress(this.buffer);
	}

	@AfterEach
	void free()
	{
		XMemory.deallocateDirectByteBuffer(this.buffer);
	}

	@Test
	void entryLengthsAreStable()
	{
		assertEquals(26, Logic.entryLengthFileCreation()  );
		assertEquals(18, Logic.entryLengthStore()         );
		assertEquals(34, Logic.entryLengthTransfer()      );
		assertEquals(34, Logic.entryLengthFileTruncation());
		assertEquals(26, Logic.entryLengthFileDeletion()  );
	}

	@Test
	void fileCreationRoundTrips()
	{
		Logic.initializeEntryFileCreation(this.address);
		Logic.setEntryFileCreation(this.address, 4096, 111, 7);

		assertEquals(StorageTransactionsEntryType.FILE_CREATION, Logic.resolveEntryType(this.address));
		assertEquals(Logic.entryLengthFileCreation(), Logic.getEntryLength(this.address));
		assertEquals(4096, Logic.getFileLength(this.address)   );
		assertEquals( 111, Logic.getEntryTimestamp(this.address));
		assertEquals(   7, Logic.getFileNumber(this.address)   );
	}

	@Test
	void storeRoundTrips()
	{
		Logic.initializeEntryStore(this.address);
		Logic.setEntryStore(this.address, 8192, 222);

		assertEquals(StorageTransactionsEntryType.DATA_STORE, Logic.resolveEntryType(this.address));
		assertEquals(Logic.entryLengthStore(), Logic.getEntryLength(this.address));
		assertEquals(8192, Logic.getFileLength(this.address)   );
		assertEquals( 222, Logic.getEntryTimestamp(this.address));
	}

	@Test
	void transferRoundTrips()
	{
		Logic.initializeEntryTransfer(this.address);
		Logic.setEntryTransfer(this.address, 12288, 333, 5, 640);

		assertEquals(StorageTransactionsEntryType.DATA_TRANSFER, Logic.resolveEntryType(this.address));
		assertEquals(Logic.entryLengthTransfer(), Logic.getEntryLength(this.address));
		assertEquals(12288, Logic.getFileLength(this.address)   );
		assertEquals(  333, Logic.getEntryTimestamp(this.address));
		assertEquals(    5, Logic.getFileNumber(this.address)   );
		assertEquals(  640, Logic.getSpecialOffset(this.address));
	}

	@Test
	void fileTruncationRoundTrips()
	{
		Logic.initializeEntryFileTruncation(this.address);
		Logic.setEntryFileTruncation(this.address, 2048, 444, 9, 4096);

		assertEquals(StorageTransactionsEntryType.FILE_TRUNCATION, Logic.resolveEntryType(this.address));
		assertEquals(Logic.entryLengthFileTruncation(), Logic.getEntryLength(this.address));
		assertEquals(2048, Logic.getFileLength(this.address)   );
		assertEquals( 444, Logic.getEntryTimestamp(this.address));
		assertEquals(   9, Logic.getFileNumber(this.address)   );
		assertEquals(4096, Logic.getSpecialOffset(this.address));
	}

	@Test
	void fileDeletionRoundTrips()
	{
		Logic.initializeEntryFileDeletion(this.address);
		Logic.setEntryFileDeletion(this.address, 1024, 555, 3);

		assertEquals(StorageTransactionsEntryType.FILE_DELETION, Logic.resolveEntryType(this.address));
		assertEquals(Logic.entryLengthFileDeletion(), Logic.getEntryLength(this.address));
		assertEquals(1024, Logic.getFileLength(this.address)   );
		assertEquals( 555, Logic.getEntryTimestamp(this.address));
		assertEquals(   3, Logic.getFileNumber(this.address)   );
	}
}
