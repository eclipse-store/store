package org.eclipse.store.gigamap.lucene;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.serializer.persistence.types.PersistenceTypeDefinition;
import org.eclipse.store.gigamap.lucene.annotations.FullText;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexLocation;
import org.eclipse.store.gigamap.types.IndexLocations;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Locations of Lucene MMap indices ({@link DirectoryCreator#MMap(IndexLocation)}): named locations, a
 * moved or copied storage, {@link LuceneIndex#changeIndexLocation(IndexLocation)}, and the stored form
 * of the location.
 */
class LuceneIndexLocationTest
{
	private static final String LOCATION = "lucene-index-location-test";

	@TempDir
	Path temp;

	@AfterEach
	void unbind()
	{
		IndexLocations.unbind(LOCATION);
	}


	///////////////////////////////////////////////////////////////////////////
	// named locations //
	////////////////////

	@Test
	void movedStorageUsesTheBoundDirectoryAndLeavesTheOriginalUntouched() throws Exception
	{
		final Path storageA = this.temp.resolve("storageA");
		final Path luceneA  = this.temp.resolve("luceneA");
		final Path storageB = this.temp.resolve("storageB");
		final Path luceneB  = this.temp.resolve("luceneB");
		IndexLocations.bind(LOCATION, luceneA);
		createStore(storageA, DirectoryCreator.MMap(IndexLocation.Named(LOCATION)));
		copyTree(storageA, storageB);
		copyTree(luceneA , luceneB );
		final Map<String, Long> original = listing(luceneA);

		IndexLocations.bind(LOCATION, luceneB);
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storageB))
		{
			final GigaMap<Article>     map    = manager.root();
			final LuceneIndex<Article> lucene = map.index().get(LuceneIndex.class);
			assertEquals(1, lucene.query("title:second").size());
			map.add(new Article("third", "gamma"));
			manager.storeRoot();
			assertEquals(1, lucene.query("title:third").size());
		}
		assertEquals(original, listing(luceneA), "the original's files must not be touched");
	}

	@Test
	void locationIsStoredAsAPlainNameField() throws Exception
	{
		// a new stored class would make older library versions fail to start; a new field does not
		final Path storage = this.temp.resolve("storage");
		IndexLocations.bind(LOCATION, this.temp.resolve("lucene"));
		createStore(storage, DirectoryCreator.MMap(IndexLocation.Named(LOCATION)));

		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final PersistenceTypeDefinition creator = manager.persistenceManager().typeDictionary()
				.lookupTypeByName(DirectoryCreator.MMapDirectoryCreator.class.getName());
			assertTrue(
				creator.allMembers().containsSearched(member ->
					"locationName".equals(member.name()) && String.class.getName().equals(member.typeName())
				),
				creator::toString
			);
			assertNull(manager.persistenceManager().typeDictionary().lookupTypeByName(IndexLocation.Absolute.class.getName()));
			assertNull(manager.persistenceManager().typeDictionary().lookupTypeByName(IndexLocation.Named.class.getName()));
			assertNull(storedPath(luceneIndex(manager)), "a named creator stores no path");
		}
	}

	@Test
	void sharedCreatorResolvesPerIndex() throws Exception
	{
		// one creator for two maps, registered while the name was bound to different directories
		final DirectoryCreator shared = DirectoryCreator.MMap(IndexLocation.Named(LOCATION));
		final Path directory1 = this.temp.resolve("lucene1");
		final Path directory2 = this.temp.resolve("lucene2");

		final GigaMap<Article> map1 = GigaMap.New();
		final GigaMap<Article> map2 = GigaMap.New();
		IndexLocations.bind(LOCATION, directory1);
		try(final LuceneIndex<Article> lucene1 = map1.index().register(LuceneIndex.Category(LuceneContext.New(shared, new ArticlePopulator()))))
		{
			IndexLocations.bind(LOCATION, directory2);
			try(final LuceneIndex<Article> lucene2 = map2.index().register(LuceneIndex.Category(LuceneContext.New(shared, new ArticlePopulator()))))
			{
				map1.add(new Article("one", "alpha"));
				map2.add(new Article("two", "beta" ));
				assertEquals(1, lucene1.query("title:one").size());
				assertEquals(0, lucene1.query("title:two").size(), "map 2's documents must not end up in map 1's files");
				assertEquals(1, lucene2.query("title:two").size());
			}
		}
		assertTrue(holdsIndex(directory1));
		assertTrue(holdsIndex(directory2));
	}

	@Test
	void locationIsResolvedAtLoadNotAtFirstUse() throws Exception
	{
		final Path storage = this.temp.resolve("storage");
		final Path luceneA = this.temp.resolve("luceneA");
		final Path luceneB = this.temp.resolve("luceneB");
		IndexLocations.bind(LOCATION, luceneA);
		createStore(storage, DirectoryCreator.MMap(IndexLocation.Named(LOCATION)));
		copyTree(luceneA, luceneB);
		final Map<String, Long> original = listing(luceneA);

		// bound to B while the storage loads, unbound before the index is used first
		IndexLocations.bind(LOCATION, luceneB);
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			IndexLocations.unbind(LOCATION);
			final GigaMap<Article>     map    = manager.root();
			final LuceneIndex<Article> lucene = map.index().get(LuceneIndex.class);
			assertEquals(1, lucene.query("title:first").size());
			map.add(new Article("third", "gamma"));
			map.store();
			assertEquals(1, lucene.query("title:third").size());
		}
		assertEquals(original, listing(luceneA));
	}

	@Test
	void unboundNameFailsTheLoad()
	{
		final Path storage = this.temp.resolve("storage");
		IndexLocations.bind(LOCATION, this.temp.resolve("lucene"));
		createStore(storage, DirectoryCreator.MMap(IndexLocation.Named(LOCATION)));
		IndexLocations.unbind(LOCATION);

		final Throwable failure = assertThrows(Throwable.class, () ->
		{
			try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
			{
				manager.root();
			}
		});
		assertTrue(causeChainContains(failure, "Lucene index", "\"" + LOCATION + "\""), failure::toString);
	}

	@Test
	void annotationHandlerWithANamedLocationUsesTheBoundDirectory() throws Exception
	{
		final Path bound = this.temp.resolve("bound");
		IndexLocations.bind(LOCATION, bound);
		final GigaMap<Note> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Note.class)
			.register(LuceneAnnotationHandler.New(IndexLocation.Named(LOCATION)))
			.generateIndices(map);

		try(final LuceneIndex<Note> lucene = map.index().get(LuceneIndex.class))
		{
			map.add(new Note("hello"));
			assertEquals(1, lucene.query("text:hello").size());
		}
		assertTrue(holdsIndex(bound), "the files must be in the bound directory");
	}

	@Test
	void unboundNameFailsTheRegistration()
	{
		final GigaMap<Article> map = GigaMap.New();
		assertThrows(IllegalStateException.class, () -> map.index().register(LuceneIndex.Category(
			LuceneContext.New(DirectoryCreator.MMap(IndexLocation.Named(LOCATION)), new ArticlePopulator())
		)));
		assertNull(map.index().get(LuceneIndex.class), "the failed registration must be rolled back");
	}


	///////////////////////////////////////////////////////////////////////////
	// changeIndexLocation //
	////////////////////////

	@Test
	void changeIndexLocationTakesEffectFromTheNextStart() throws Exception
	{
		final Path storage = this.temp.resolve("storage");
		final Path luceneA = this.temp.resolve("luceneA");
		final Path luceneB = this.temp.resolve("luceneB");
		createStore(storage, DirectoryCreator.MMap(luceneA));

		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Article>     map    = manager.root();
			final LuceneIndex<Article> lucene = map.index().get(LuceneIndex.class);
			assertEquals(1, lucene.query("title:first").size());
			lucene.changeIndexLocation(IndexLocation.Named(LOCATION));

			// a close and reopen in the same run must not move the files
			lucene.close();
			assertEquals(1, lucene.query("title:first").size());
			map.store();
		}

		copyTree(luceneA, luceneB);
		final Map<String, Long> original = listing(luceneA);
		IndexLocations.bind(LOCATION, luceneB);
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Article>     map    = manager.root();
			final LuceneIndex<Article> lucene = map.index().get(LuceneIndex.class);
			assertEquals(1, lucene.query("title:second").size(), "the change must reach the storage");
			// an older library version must fail on the index instead of searching outdated files there
			assertNull(storedPath(lucene), "the old path must not be kept");
			map.add(new Article("third", "gamma"));
			manager.storeRoot();
		}
		assertEquals(original, listing(luceneA), "the previous directory must not be touched");
	}

	@Test
	void changeBeforeTheFirstOpenStaysInTheOldDirectoryForThisRun() throws Exception
	{
		final Path storage = this.temp.resolve("storage");
		final Path luceneA = this.temp.resolve("luceneA");
		final Path luceneB = this.temp.resolve("luceneB");
		createStore(storage, DirectoryCreator.MMap(luceneA));

		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Article>     map    = manager.root();
			final LuceneIndex<Article> lucene = map.index().get(LuceneIndex.class);
			lucene.changeIndexLocation(IndexLocation.Absolute(luceneB));
			assertEquals(1, lucene.query("title:first").size(), "still the old files in this run");
			map.add(new Article("third", "gamma"));
			map.store();
		}
		assertFalse(Files.exists(luceneB), "nothing may be written to the new location in this run");

		copyTree(luceneA, luceneB);
		final Map<String, Long> original = listing(luceneA);
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Article>     map    = manager.root();
			final LuceneIndex<Article> lucene = map.index().get(LuceneIndex.class);
			assertEquals(1, lucene.query("title:third").size(), "the next start uses the new location");
			map.add(new Article("fourth", "delta"));
			map.store();
		}
		assertEquals(original, listing(luceneA), "the old directory must not be touched from the next start on");
	}

	@Test
	void changeIndexLocationToTheCurrentLocationChangesNothing()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> lucene = map.index().register(LuceneIndex.Category(
			LuceneContext.New(DirectoryCreator.MMap(this.temp.resolve("lucene")), new ArticlePopulator())
		)))
		{
			final LuceneContext<Article> before = ((LuceneIndex.Default<Article>)lucene).context;
			lucene.changeIndexLocation(IndexLocation.Absolute(this.temp.resolve("lucene")));
			assertSame(before, ((LuceneIndex.Default<Article>)lucene).context);
		}
	}

	@Test
	void changeIndexLocationRejectsIndicesWithoutAnMMapDirectory()
	{
		final IndexLocation named = IndexLocation.Named(LOCATION);
		final DirectoryCreator custom = new CustomDirectoryCreator();
		for(final DirectoryCreator creator : new DirectoryCreator[]{ null, DirectoryCreator.ByteBuffers(), custom })
		{
			final GigaMap<Article> map = GigaMap.New();
			try(final LuceneIndex<Article> lucene = map.index().register(LuceneIndex.Category(
				LuceneContext.New(creator, AnalyzerCreator.Standard(), new ArticlePopulator())
			)))
			{
				final IllegalStateException failure = assertThrows(IllegalStateException.class,
					() -> lucene.changeIndexLocation(named));
				assertTrue(failure.getMessage().contains("keeps no files in a directory"), failure::getMessage);
			}
		}
	}

	@Test
	void changeIndexLocationRejectsAReadOnlyMap()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> lucene = map.index().register(LuceneIndex.Category(
			LuceneContext.New(DirectoryCreator.MMap(this.temp.resolve("lucene")), new ArticlePopulator())
		)))
		{
			map.markReadOnly();
			try
			{
				assertThrows(IllegalStateException.class, () -> lucene.changeIndexLocation(IndexLocation.Named(LOCATION)));
			}
			finally
			{
				map.unmarkReadOnly();
			}
		}
	}

	@Test
	void changeIndexLocationRejectsACustomContext()
	{
		// the context is replaced by a copy, which would drop whatever a custom context holds or does
		final LuceneContext<Article> defaults = LuceneContext.New(DirectoryCreator.MMap(this.temp.resolve("lucene")), new ArticlePopulator());
		final LuceneContext<Article> custom   = new LuceneContext<>()
		{
			@Override
			public DirectoryCreator directoryCreator()
			{
				return defaults.directoryCreator();
			}

			@Override
			public AnalyzerCreator analyzerCreator()
			{
				return defaults.analyzerCreator();
			}

			@Override
			public DocumentPopulator<Article> documentPopulator()
			{
				return defaults.documentPopulator();
			}
		};
		assertRefusesToCopy(custom);
	}

	@Test
	void changeIndexLocationRejectsASubclassOfTheDefaultContext()
	{
		// e.g. one that overrides autoCommit(): a plain copy would silently switch to auto-commit
		final LuceneContext<Article> subclass = new LuceneContext.Default<>(
			DirectoryCreator.MMap(this.temp.resolve("lucene")),
			AnalyzerCreator.Standard(),
			new ArticlePopulator()
		)
		{
			@Override
			public boolean autoCommit()
			{
				return false;
			}
		};
		assertRefusesToCopy(subclass);
	}


	///////////////////////////////////////////////////////////////////////////
	// fixtures //
	/////////////

	static final class Article
	{
		final String title;
		final String content;

		Article(final String title, final String content)
		{
			super();
			this.title   = title;
			this.content = content;
		}
	}

	static final class Note
	{
		@FullText
		final String text;

		Note(final String text)
		{
			super();
			this.text = text;
		}
	}

	/** An application's own creator, whose location the index cannot know. */
	static final class CustomDirectoryCreator extends DirectoryCreator
	{
		@Override
		public Directory createDirectory()
		{
			return new ByteBuffersDirectory();
		}
	}

	static final class ArticlePopulator extends DocumentPopulator<Article>
	{
		@Override
		public void populate(final Document document, final Article entity)
		{
			document.add(createTextField("title"  , entity.title  ));
			document.add(createTextField("content", entity.content));
		}
	}

	private void assertRefusesToCopy(final LuceneContext<Article> context)
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> lucene = map.index().register(LuceneIndex.Category(context)))
		{
			assertThrows(UnsupportedOperationException.class, () -> lucene.changeIndexLocation(IndexLocation.Named(LOCATION)));
			assertSame(context, ((LuceneIndex.Default<Article>)lucene).context);
		}
	}

	private static void createStore(final Path storage, final DirectoryCreator creator)
	{
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Article> map = GigaMap.New();
			manager.setRoot(map);
			map.index().register(LuceneIndex.Category(LuceneContext.New(creator, new ArticlePopulator())));
			map.add(new Article("first" , "alpha"));
			map.add(new Article("second", "beta" ));
			manager.storeRoot();
		}
	}

	private static LuceneIndex<Article> luceneIndex(final EmbeddedStorageManager manager)
	{
		return manager.<GigaMap<Article>>root().index().get(LuceneIndex.class);
	}

	/**
	 * The path an MMap creator keeps in the storage. There is deliberately no accessor for it: what an
	 * index uses is its resolved location, the stored path only matters to older library versions.
	 */
	private static Path storedPath(final LuceneIndex<?> lucene) throws ReflectiveOperationException
	{
		final DirectoryCreator creator = ((LuceneIndex.Default<?>)lucene).context.directoryCreator();
		final Field            path    = DirectoryCreator.MMapDirectoryCreator.class.getDeclaredField("path");
		path.setAccessible(true);
		return (Path)path.get(creator);
	}

	private static boolean holdsIndex(final Path path) throws IOException
	{
		try(final Directory directory = new MMapDirectory(path))
		{
			return DirectoryReader.indexExists(directory);
		}
	}

	/** Whether one exception in the cause chain has a message containing all the given parts. */
	private static boolean causeChainContains(final Throwable failure, final String... parts)
	{
		for(Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause())
		{
			final String message = cause.getMessage();
			if(message != null && Stream.of(parts).allMatch(message::contains))
			{
				return true;
			}
		}
		return false;
	}

	private static Map<String, Long> listing(final Path directory) throws IOException
	{
		final Map<String, Long> result = new TreeMap<>();
		try(final Stream<Path> files = Files.list(directory))
		{
			for(final Path file : (Iterable<Path>)files::iterator)
			{
				result.put(file.getFileName().toString(), Files.size(file));
			}
		}
		return result;
	}

	private static void copyTree(final Path source, final Path target) throws IOException
	{
		try(final Stream<Path> paths = Files.walk(source))
		{
			for(final Path path : (Iterable<Path>)paths::iterator)
			{
				final Path destination = target.resolve(source.relativize(path).toString());
				if(Files.isDirectory(path))
				{
					Files.createDirectories(destination);
				}
				else
				{
					Files.copy(path, destination);
				}
			}
		}
	}

}
