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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.LongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.types.StorageTransactionsAnalysis.EntryAggregator;
import org.eclipse.store.storage.types.StorageTransactionsAnalysis.Logic;
import org.eclipse.store.storage.types.StorageTransactionsEntries;
import org.eclipse.store.storage.types.StorageTransactionsEntryType;

/**
 * Shared harness for the power-loss and durability recovery tests.
 * <p>
 * A power loss is simulated deterministically: run the storage, shut it down cleanly (everything
 * flushed), then edit the on-disk files to the exact state a crash with a given flush order would
 * leave - append log entries the engine itself would have written, truncate what never reached the
 * medium - and restart. The log entries are synthesized byte-exactly through the engine's own
 * {@link Logic} writers, so the surgery cannot drift from the real format.
 */
final class RecoverySimulation
{
	///////////////////////////////////////////////////////////////////////////
	// configuration //
	//////////////////

	/** Multi-channel storage with a small file size (frequent rollovers) and immediate housekeeping. */
	static StorageConfiguration config(final Path directory, final int channelCount, final int maxFileSize)
	{
		return StorageConfiguration.Builder()
			.setStorageFileProvider(Storage.FileProvider(directory))
			.setChannelCountProvider(Storage.ChannelCountProvider(channelCount))
			.setHousekeepingController(Storage.HousekeepingController(1_000L, 10_000_000L))
			.setDataFileEvaluator(Storage.DataFileEvaluator(1024, maxFileSize, 0.75))
			.createConfiguration();
	}

	/**
	 * Like {@link #config} but with periodic housekeeping starved to a token budget, so file checks,
	 * garbage collection and dissolution only run when a test triggers them explicitly. Keeps
	 * staging deterministic.
	 */
	static StorageConfiguration quietConfig(final Path directory, final int channelCount, final int maxFileSize)
	{
		return StorageConfiguration.Builder()
			.setStorageFileProvider(Storage.FileProvider(directory))
			.setChannelCountProvider(Storage.ChannelCountProvider(channelCount))
			.setHousekeepingController(Storage.HousekeepingController(60L * 60L * 1000L, 1_000L))
			.setDataFileEvaluator(Storage.DataFileEvaluator(1024, maxFileSize, 0.75))
			.createConfiguration();
	}


	///////////////////////////////////////////////////////////////////////////
	// channel / object id //
	////////////////////////

	/** The channel an object id maps to (channel count is always a power of two). */
	static int channelOf(final long objectId, final int channelCount)
	{
		return (int)(objectId & (channelCount - 1));
	}

	static long objectId(final PersistenceObjectRegistry registry, final Object instance)
	{
		final long objectId = registry.lookupObjectId(instance);
		assertTrue(objectId > 0, "object id unresolved for " + instance.getClass().getSimpleName());
		return objectId;
	}


	///////////////////////////////////////////////////////////////////////////
	// file locations //
	///////////////////

	static Path transactionsFile(final Path directory, final int channel)
	{
		final Path file = directory.resolve("channel_" + channel).resolve("transactions_" + channel + ".sft");
		assertTrue(Files.exists(file), "no transactions file " + file);
		return file;
	}

	/** The channel's data files by file number, ascending. */
	static NavigableMap<Long, Path> dataFiles(final Path directory, final int channel)
	{
		final Path                     channelDir = directory.resolve("channel_" + channel);
		final Pattern                  pattern    = Pattern.compile("channel_" + channel + "_(\\d+)\\.dat");
		final NavigableMap<Long, Path> files      = new TreeMap<>();
		try(Stream<Path> list = Files.list(channelDir))
		{
			list.forEach(path ->
			{
				final Matcher matcher = pattern.matcher(path.getFileName().toString());
				if(matcher.matches())
				{
					files.put(Long.parseLong(matcher.group(1)), path);
				}
			});
		}
		catch(final IOException e)
		{
			throw new UncheckedIOException(e);
		}
		assertTrue(!files.isEmpty(), "no data files under " + channelDir);
		return files;
	}


	///////////////////////////////////////////////////////////////////////////
	// surgery //
	////////////

	static long size(final Path file)
	{
		try
		{
			return Files.size(file);
		}
		catch(final IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	static void truncate(final Path file, final long length)
	{
		try(FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE))
		{
			channel.truncate(length);
		}
		catch(final IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	static void append(final Path file, final byte[] bytes)
	{
		try
		{
			Files.write(file, bytes, StandardOpenOption.APPEND);
		}
		catch(final IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	static void delete(final Path file)
	{
		try
		{
			Files.delete(file);
		}
		catch(final IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	/** Truncates the channel's transactions log to drop its trailing {@code lost} store entries. */
	static void cutTrailingStores(final Path directory, final int channel, final int lost)
	{
		final AFile logFile = Storage.FileProvider(directory).provideTransactionsFile(channel);
		final Iterable<? extends StorageTransactionsEntries.Entry> entries =
			StorageTransactionsEntries.parseFile(logFile).entries();

		long totalStores = 0;
		for(final StorageTransactionsEntries.Entry entry : entries)
		{
			if(entry.type() == StorageTransactionsEntryType.DATA_STORE)
			{
				totalStores++;
			}
		}
		assertTrue(totalStores > lost, "not enough store entries to cut");

		final long keep = totalStores - lost;
		long offset = 0;
		long seen   = 0;
		for(final StorageTransactionsEntries.Entry entry : entries)
		{
			offset += entry.type().length();
			if(entry.type() == StorageTransactionsEntryType.DATA_STORE && ++seen == keep)
			{
				break;
			}
		}
		truncate(transactionsFile(directory, channel), offset);
	}

	/** Truncates the channel's log to drop ONLY its last store entry, keeping any trailing non-store entry. */
	static void cutLastStore(final Path directory, final int channel)
	{
		final AFile logFile = Storage.FileProvider(directory).provideTransactionsFile(channel);
		final Iterable<? extends StorageTransactionsEntries.Entry> entries =
			StorageTransactionsEntries.parseFile(logFile).entries();

		long offset          = 0;
		long lastStoreOffset = -1;
		for(final StorageTransactionsEntries.Entry entry : entries)
		{
			if(entry.type() == StorageTransactionsEntryType.DATA_STORE)
			{
				lastStoreOffset = offset;
			}
			offset += entry.type().length();
		}
		assertTrue(lastStoreOffset >= 0, "no store entry to cut");
		truncate(transactionsFile(directory, channel), lastStoreOffset);
	}

	static byte[] readRange(final Path file, final long offset, final long length)
	{
		final ByteBuffer buffer = ByteBuffer.allocate((int)length);
		try(FileChannel channel = FileChannel.open(file, StandardOpenOption.READ))
		{
			while(buffer.hasRemaining())
			{
				if(channel.read(buffer, offset + buffer.position()) < 0)
				{
					throw new IOException("unexpected end of file in " + file);
				}
			}
		}
		catch(final IOException e)
		{
			throw new UncheckedIOException(e);
		}
		return buffer.array();
	}

	/** One binary storage record: 24-byte header [length][typeId][objectId] followed by the payload. */
	record EntityRecord(long offset, long length, long typeId, long objectId)
	{
		// carrier only
	}

	/** Walks the entity records of a data file, skipping gaps and stopping at a torn or zero tail. */
	static List<EntityRecord> walkEntities(final Path dataFile)
	{
		final byte[] bytes;
		try
		{
			bytes = Files.readAllBytes(dataFile);
		}
		catch(final IOException e)
		{
			throw new UncheckedIOException(e);
		}
		final ByteBuffer         buffer  = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
		final List<EntityRecord> records = new ArrayList<>();
		long position = 0;
		while(position + Long.BYTES <= bytes.length)
		{
			final long length = buffer.getLong((int)position);
			if(length == 0)
			{
				break; // zero-filled tail
			}
			if(length < 0)
			{
				position += -length; // gap
				continue;
			}
			if(position + length > bytes.length)
			{
				break; // torn tail
			}
			records.add(new EntityRecord(
				position,
				length,
				buffer.getLong((int)position + 8),
				buffer.getLong((int)position + 16)
			));
			position += length;
		}
		return records;
	}


	///////////////////////////////////////////////////////////////////////////
	// transactions-log entry synthesis //
	/////////////////////////////////////

	static byte[] fileCreationEntry(final long fileLength, final long timestamp, final long fileNumber)
	{
		return entry(Logic.entryLengthFileCreation(), address ->
		{
			Logic.initializeEntryFileCreation(address);
			Logic.setEntryFileCreation(address, fileLength, timestamp, fileNumber);
		});
	}

	static byte[] storeEntry(final long fileLength, final long timestamp)
	{
		return entry(Logic.entryLengthStore(), address ->
		{
			Logic.initializeEntryStore(address);
			Logic.setEntryStore(address, fileLength, timestamp);
		});
	}

	static byte[] fileTruncationEntry(
		final long newLength ,
		final long timestamp ,
		final long fileNumber,
		final long oldLength
	)
	{
		return entry(Logic.entryLengthFileTruncation(), address ->
		{
			Logic.initializeEntryFileTruncation(address);
			Logic.setEntryFileTruncation(address, newLength, timestamp, fileNumber, oldLength);
		});
	}

	static byte[] transferEntry(
		final long newFileLength   ,
		final long timestamp       ,
		final long sourceFileNumber,
		final long sourceFileOffset
	)
	{
		return entry(Logic.entryLengthTransfer(), address ->
		{
			Logic.initializeEntryTransfer(address);
			Logic.setEntryTransfer(address, newFileLength, timestamp, sourceFileNumber, sourceFileOffset);
		});
	}

	static byte[] fileDeletionEntry(final long fileLength, final long timestamp, final long fileNumber)
	{
		return entry(Logic.entryLengthFileDeletion(), address ->
		{
			Logic.initializeEntryFileDeletion(address);
			Logic.setEntryFileDeletion(address, fileLength, timestamp, fileNumber);
		});
	}

	private static byte[] entry(final int length, final LongConsumer writer)
	{
		final ByteBuffer buffer = XMemory.allocateDirectNative(length);
		try
		{
			writer.accept(XMemory.getDirectByteBufferAddress(buffer));
			final byte[] bytes = new byte[length];
			buffer.get(bytes);
			return bytes;
		}
		finally
		{
			XMemory.deallocateDirectByteBuffer(buffer);
		}
	}


	///////////////////////////////////////////////////////////////////////////
	// white-box log replay //
	/////////////////////////

	/** Feeds a single synthesized log entry to an aggregator, exactly as the read loop would. */
	static void feed(final EntryAggregator aggregator, final byte[] entry)
	{
		final ByteBuffer buffer = XMemory.allocateDirectNative(entry.length);
		try
		{
			final long address = XMemory.getDirectByteBufferAddress(buffer);
			buffer.put(entry);
			aggregator.accept(address, entry.length);
		}
		finally
		{
			XMemory.deallocateDirectByteBuffer(buffer);
		}
	}

	/** Reads a private long field of an aggregator - the anchors are not otherwise observable. */
	static long aggregatorField(final EntryAggregator aggregator, final String name)
	{
		try
		{
			final Field field = EntryAggregator.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.getLong(aggregator);
		}
		catch(final ReflectiveOperationException e)
		{
			throw new AssertionError("cannot read EntryAggregator." + name, e);
		}
	}


	private RecoverySimulation()
	{
		throw new UnsupportedOperationException();
	}
}
