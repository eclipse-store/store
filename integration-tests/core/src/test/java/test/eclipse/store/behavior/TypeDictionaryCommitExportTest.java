package test.eclipse.store.behavior;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryFileHandler;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryIoHandler;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryStorer;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageFileWriter;
import org.eclipse.store.storage.types.StorageLiveDataFile;
import org.eclipse.store.storage.types.StorageLiveFileProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The type dictionary is exported once per store commit, and always before that commit's data
 * reaches the medium.
 * <p>
 * Entities carry type ids only, so the persisted dictionary has to describe every type id a data
 * file contains. A dictionary written after the data opens a crash window in which committed
 * entities cannot be resolved on the next start; a dictionary written per registration closes that
 * window too, but makes a store that discovers many types pay the crash-safe write (temporary file,
 * fsync, swap) once per type instead of once per commit.
 * <p>
 * Fix-guard: {@link #oneExportPerCommit()} fails when registrations export individually;
 * {@link #dictionaryReachesDiskBeforeData()} and {@link #laterCommitExportsItsOwnNewType()} fail when
 * the export is deferred past the data write.
 */
@Timeout(120)
class TypeDictionaryCommitExportTest
{
	private static final int INSTANCES_PER_TYPE = 25;

	/**
	 * Every type {@link #fillWithEveryType(List)} instantiates; the two must stay in sync, the entity
	 * count of a filled root is derived from this length.
	 */
	private static final Class<?>[] GRAPH_TYPES =
	{
		Alpha.class, Beta.class, Gamma.class, Delta.class,
		Epsilon.class, Zeta.class, Eta.class, Theta.class
	};

	@TempDir
	Path directory;

	private ExportCounter     exports   ;
	private DataWriteObserver dataWrites;

	private EmbeddedStorageManager storage ;
	private EmbeddedStorageManager reloaded;

	@BeforeEach
	void setUp()
	{
		this.exports    = new ExportCounter();
		this.dataWrites = new DataWriteObserver(this.dictionaryFile());
	}

	@AfterEach
	void shutdown()
	{
		for(final EmbeddedStorageManager manager : new EmbeddedStorageManager[]{this.reloaded, this.storage})
		{
			if(manager != null && manager.isRunning())
			{
				try
				{
					manager.shutdown();
				}
				catch(final RuntimeException ignored)
				{
					// best effort: a failing shutdown must not hide the test's own outcome, and must not
					// keep the remaining manager from being closed and its files from being released.
				}
			}
		}
	}



	///////////////////////////////////////////////////////////////////////////
	// tests //
	//////////

	/**
	 * Coalescing: a single commit that registers eight types writes the dictionary once, not once per
	 * registered type.
	 */
	@Test
	void oneExportPerCommit()
	{
		final List<Object> root = new ArrayList<>();
		this.storage = this.start(root);

		// the start-up commit of the roots has its own export, which is not what this case measures.
		this.exports.reset();

		fillWithEveryType(root);
		this.storage.storeRoot();

		assertEquals(
			1,
			this.exports.count(),
			"a commit registering " + GRAPH_TYPES.length + " types must export the dictionary exactly once"
		);
	}

	/**
	 * Crash-safety ordering: when the first data of a commit is handed to the target, the dictionary on
	 * disk already describes the types that data uses.
	 */
	@Test
	void dictionaryReachesDiskBeforeData()
	{
		final List<Object> root = new ArrayList<>();
		this.storage = this.start(root);

		final String dictionaryAtStartUp = this.dataWrites.dictionaryAtFirstDataWrite();
		assertNotNull(dictionaryAtStartUp, "starting an empty storage is expected to write its roots");
		assertFalse(
			dictionaryAtStartUp.isEmpty(),
			"the start-up commit must not write data before its dictionary exists on disk"
		);

		fillWithEveryType(root);
		this.dataWrites.reset();
		this.storage.storeRoot();

		final String dictionary = this.dataWrites.dictionaryAtFirstDataWrite();
		assertNotNull(dictionary, "the commit is expected to write data");
		for(final Class<?> type : GRAPH_TYPES)
		{
			assertTrue(
				dictionary.contains(type.getName()),
				() -> "dictionary lacked " + type.getName() + " when this commit's first data was written"
			);
		}
	}

	/**
	 * A type discovered by a later commit is exported by that commit - the flush is per commit, not a
	 * one-off during start-up.
	 */
	@Test
	void laterCommitExportsItsOwnNewType()
	{
		final List<Object> root = new ArrayList<>();
		this.storage = this.start(root);
		fillWithEveryType(root);
		this.storage.storeRoot();

		this.exports.reset();
		this.dataWrites.reset();
		root.add(new Omega("late"));
		this.storage.storeRoot();

		assertEquals(1, this.exports.count(), "the commit introducing a new type must export once");

		final String dictionary = this.dataWrites.dictionaryAtFirstDataWrite();
		assertNotNull(dictionary, "the commit is expected to write data");
		assertTrue(
			dictionary.contains(Omega.class.getName()),
			"the new type must be on disk before the data referencing it"
		);
	}

	/**
	 * Nothing new to describe, nothing to write: a commit without a registration leaves the dictionary
	 * file alone.
	 */
	@Test
	void commitWithoutNewTypeDoesNotExport()
	{
		final List<Object> root = new ArrayList<>();
		this.storage = this.start(root);
		fillWithEveryType(root);
		this.storage.storeRoot();

		this.exports.reset();
		root.add(new Alpha(-1));
		this.storage.storeRoot();

		assertEquals(0, this.exports.count(), "a commit of already known types must not export the dictionary");
	}

	/**
	 * Completeness: the single coalesced export describes the whole graph, so a restart resolves every
	 * type id the commit wrote.
	 */
	@Test
	void restartResolvesEveryStoredType()
	{
		final List<Object> root = new ArrayList<>();
		this.storage = this.start(root);
		fillWithEveryType(root);
		this.storage.storeRoot();
		this.storage.shutdown();
		this.storage = null;

		this.reloaded = EmbeddedStorage.start(this.directory);

		final List<Object> loaded = this.reloaded.root();
		assertEquals(INSTANCES_PER_TYPE * GRAPH_TYPES.length, loaded.size(), "every stored entity must reload");

		final Theta theta = (Theta)loaded.get(loaded.size() - 1);
		assertEquals("th" + (INSTANCES_PER_TYPE - 1), theta.eta.name, "the deepest reloaded value must match");
	}

	/**
	 * Concurrent commits each contribute their type to the dictionary. The change mark is tested,
	 * exported and cleared under the manager's monitor, so a registration racing another commit's flush
	 * cannot be dropped.
	 * <p>
	 * Cross-thread timing cannot be forced from the outside here, so this covers the contract rather
	 * than the interleaving: it holds for every schedule, and no schedule may lose a type.
	 */
	@Test
	void concurrentCommitsExportEveryType() throws InterruptedException
	{
		final List<List<Object>> root = new ArrayList<>();
		for(int i = 0; i < 3; i++)
		{
			root.add(new ArrayList<>());
		}
		this.storage = this.start(root);

		final Object[]        news     = {new ConcurrentA(1), new ConcurrentB(2), new ConcurrentC(3)};
		final CountDownLatch  start    = new CountDownLatch(1);
		final List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		final Thread[]        users    = new Thread[news.length];
		for(int i = 0; i < news.length; i++)
		{
			final List<Object> target   = root.get(i);
			final Object       instance = news[i];
			users[i] = new Thread(() ->
			{
				try
				{
					start.await();
					target.add(instance);
					this.storage.store(target);
				}
				catch(final InterruptedException e)
				{
					Thread.currentThread().interrupt();
					failures.add(e);
				}
				catch(final Throwable t)
				{
					failures.add(t);
				}
			});
			users[i].start();
		}

		start.countDown();
		for(final Thread user : users)
		{
			user.join(TimeUnit.SECONDS.toMillis(60));
			assertFalse(user.isAlive(), "a storing thread did not finish in time");
		}
		assertTrue(failures.isEmpty(), () -> "a storing thread failed: " + failures);

		final String dictionary = this.dictionaryText();
		for(final Object instance : news)
		{
			assertTrue(
				dictionary.contains(instance.getClass().getName()),
				() -> "concurrently registered " + instance.getClass().getName() + " is missing from the dictionary"
			);
		}
	}



	///////////////////////////////////////////////////////////////////////////
	// helpers //
	////////////

	private EmbeddedStorageManager start(final Object root)
	{
		final NioFileSystem fileSystem = NioFileSystem.New();

		final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(
			Storage.Configuration(
				StorageLiveFileProvider.Builder(fileSystem)
					.setDirectory(fileSystem.ensureDirectoryPath(this.directory.toString()))
					.setFileHandlerCreator(this.exports)
					.createFileProvider()
			)
		);
		foundation.setWriterProvider(this.dataWrites);

		return foundation.createEmbeddedStorageManager(root).start();
	}

	private Path dictionaryFile()
	{
		return this.directory.resolve(Persistence.defaultFilenameTypeDictionary());
	}

	private String dictionaryText()
	{
		return readDictionary(this.dictionaryFile());
	}

	/**
	 * Never throws: this also runs on channel threads inside a write, where an exception would abort the
	 * very commit under observation. Anything unreadable counts as "not on disk".
	 */
	private static String readDictionary(final Path file)
	{
		final String live = readIfReadable(file);
		if(live != null)
		{
			return live;
		}

		// while the swap is in flight the live file is gone and the complete export is its temporary
		// sibling, which is what the storage itself would read at that moment, too.
		final String temporary = readIfReadable(
			file.resolveSibling(file.getFileName() + PersistenceTypeDictionaryFileHandler.temporaryFileSuffix())
		);

		return temporary != null
			? temporary
			: ""
		;
	}

	private static String readIfReadable(final Path file)
	{
		try
		{
			return Files.readString(file, Persistence.standardCharset());
		}
		catch(final IOException e)
		{
			return null;
		}
	}

	private static void fillWithEveryType(final List<Object> root)
	{
		for(int i = 0; i < INSTANCES_PER_TYPE; i++)
		{
			root.add(new Alpha(i));
			root.add(new Beta("b" + i));
			root.add(new Gamma(new Alpha(i), new Beta("g" + i)));
			root.add(new Delta(i));
			root.add(new Epsilon(i / 2.0));
			root.add(new Zeta(new Gamma(new Alpha(i), new Beta("z" + i))));
			root.add(new Eta(i, "e" + i));
			root.add(new Theta(new Zeta(new Gamma(new Alpha(i), new Beta("t" + i))), new Eta(i, "th" + i)));
		}
	}



	///////////////////////////////////////////////////////////////////////////
	// observers //
	//////////////

	/**
	 * Keeps a tally of how often the dictionary is written. The production handler performs the write
	 * itself, so what is measured is the real thing and not a stand-in.
	 */
	static final class ExportCounter implements PersistenceTypeDictionaryFileHandler.Creator
	{
		private final AtomicInteger exportCount = new AtomicInteger();

		@Override
		public PersistenceTypeDictionaryIoHandler createTypeDictionaryIoHandler(
			final AFile                           file         ,
			final PersistenceTypeDictionaryStorer writeListener
		)
		{
			return new TallyingDictionaryIo(
				PersistenceTypeDictionaryFileHandler.New(file, writeListener),
				this.exportCount
			);
		}

		int count()
		{
			return this.exportCount.get();
		}

		void reset()
		{
			this.exportCount.set(0);
		}
	}

	static final class TallyingDictionaryIo implements PersistenceTypeDictionaryIoHandler
	{
		private final PersistenceTypeDictionaryIoHandler actual     ;
		private final AtomicInteger                      exportCount;

		TallyingDictionaryIo(final PersistenceTypeDictionaryIoHandler actual, final AtomicInteger exportCount)
		{
			super();
			this.actual      = actual     ;
			this.exportCount = exportCount;
		}

		@Override
		public String loadTypeDictionary()
		{
			return this.actual.loadTypeDictionary();
		}

		@Override
		public void storeTypeDictionary(final String typeDictionaryString)
		{
			this.exportCount.incrementAndGet();
			this.actual.storeTypeDictionary(typeDictionaryString);
		}
	}

	/**
	 * Captures the dictionary as it is on disk when a commit hands its first data to the target.
	 */
	static final class DataWriteObserver implements StorageFileWriter.Provider
	{
		private final Path                    dictionaryFile;
		private final AtomicReference<String> firstSeen = new AtomicReference<>();

		DataWriteObserver(final Path dictionaryFile)
		{
			super();
			this.dictionaryFile = dictionaryFile;
		}

		@Override
		public StorageFileWriter provideWriter()
		{
			return new ObservingWriter(this);
		}

		void observeDataWrite()
		{
			if(this.firstSeen.get() == null)
			{
				this.firstSeen.compareAndSet(null, readDictionary(this.dictionaryFile));
			}
		}

		String dictionaryAtFirstDataWrite()
		{
			return this.firstSeen.get();
		}

		void reset()
		{
			this.firstSeen.set(null);
		}
	}

	static final class ObservingWriter implements StorageFileWriter
	{
		private final DataWriteObserver observer;

		ObservingWriter(final DataWriteObserver observer)
		{
			super();
			this.observer = observer;
		}

		@Override
		public long writeStore(
			final StorageLiveDataFile            targetFile ,
			final Iterable<? extends ByteBuffer> byteBuffers
		)
		{
			this.observer.observeDataWrite();

			return StorageFileWriter.super.writeStore(targetFile, byteBuffers);
		}
	}



	///////////////////////////////////////////////////////////////////////////
	// entities //
	/////////////

	static final class Alpha
	{
		final int value;

		Alpha(final int value)
		{
			super();
			this.value = value;
		}
	}

	static final class Beta
	{
		final String name;

		Beta(final String name)
		{
			super();
			this.name = name;
		}
	}

	static final class Gamma
	{
		final Alpha alpha;
		final Beta  beta ;

		Gamma(final Alpha alpha, final Beta beta)
		{
			super();
			this.alpha = alpha;
			this.beta  = beta ;
		}
	}

	static final class Delta
	{
		final long value;

		Delta(final long value)
		{
			super();
			this.value = value;
		}
	}

	static final class Epsilon
	{
		final double value;

		Epsilon(final double value)
		{
			super();
			this.value = value;
		}
	}

	static final class Zeta
	{
		final Gamma gamma;

		Zeta(final Gamma gamma)
		{
			super();
			this.gamma = gamma;
		}
	}

	static final class Eta
	{
		final int    value;
		final String name ;

		Eta(final int value, final String name)
		{
			super();
			this.value = value;
			this.name  = name ;
		}
	}

	static final class Theta
	{
		final Zeta zeta;
		final Eta  eta ;

		Theta(final Zeta zeta, final Eta eta)
		{
			super();
			this.zeta = zeta;
			this.eta  = eta ;
		}
	}

	static final class Omega
	{
		final String name;

		Omega(final String name)
		{
			super();
			this.name = name;
		}
	}

	static final class ConcurrentA
	{
		final int value;

		ConcurrentA(final int value)
		{
			super();
			this.value = value;
		}
	}

	static final class ConcurrentB
	{
		final int value;

		ConcurrentB(final int value)
		{
			super();
			this.value = value;
		}
	}

	static final class ConcurrentC
	{
		final int value;

		ConcurrentC(final int value)
		{
			super();
			this.value = value;
		}
	}

}
