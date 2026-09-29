package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.serializer.persistence.types.PersistenceTypeDefinition;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionary;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.gigamap.jvector.annotations.Vector;
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
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Locations of on-disk vector indices ({@link IndexLocation}): absolute and named locations, a moved
 * or copied storage, {@link VectorIndices#changeIndexLocation(String, IndexLocation)}, and the stored
 * form of the location.
 */
class VectorIndexLocationTest
{
	private static final int    DIMENSION = 8;
	private static final int    COUNT     = 300;
	private static final String INDEX     = "embeddings";
	private static final String LOCATION  = "vector-index-location-test";

	@TempDir
	Path temp;

	@AfterEach
	void unbind()
	{
		IndexLocations.unbind(LOCATION);
	}


	///////////////////////////////////////////////////////////////////////////
	// absolute and named locations //
	/////////////////////////////////

	@Test
	void absoluteLocationBehavesAsAPlainDirectory() throws Exception
	{
		final Path storage = this.temp.resolve("storage");
		final Path index   = this.temp.resolve("index");
		createStore(storage, onDisk(IndexLocation.Absolute(index)));

		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final VectorIndex<Doc> vectorIndex = vectorIndex(manager.root());
			assertNull(vectorIndex.configuration().indexLocationName());
			assertEquals(IndexLocation.Absolute(index), vectorIndex.configuration().indexLocation());
			assertEquals(index, vectorIndex.indexDirectory());
			assertEquals(5, vectorIndex.search(queryVector(), 5).size());
			assertTrue(isIncrementalMode(vectorIndex), "the graph must be loaded, not rebuilt");
		}
	}

	@Test
	void movedStorageLoadsTheCopiedGraphAndLeavesTheOriginalUntouched() throws Exception
	{
		final Path storageA = this.temp.resolve("storageA");
		final Path indexA   = this.temp.resolve("indexA");
		final Path storageB = this.temp.resolve("storageB");
		final Path indexB   = this.temp.resolve("indexB");
		IndexLocations.bind(LOCATION, indexA);
		createStore(storageA, onDisk(IndexLocation.Named(LOCATION)));
		copyTree(storageA, storageB);
		copyTree(indexA  , indexB  );
		final byte[] originalMeta = Files.readAllBytes(indexA.resolve(INDEX + ".meta"));

		IndexLocations.bind(LOCATION, indexB);
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storageB))
		{
			final GigaMap<Doc>     map         = manager.root();
			final VectorIndex<Doc> vectorIndex = vectorIndex(map);
			assertEquals(indexB, vectorIndex.indexDirectory());
			assertTrue(isIncrementalMode(vectorIndex), "the copied graph must be loaded, not rebuilt");
			assertEquals(5, vectorIndex.search(queryVector(), 5).size());

			map.add(new Doc(vector(new Random(5))));
			vectorIndex.persistToDisk();
		}
		assertArrayEquals(originalMeta, Files.readAllBytes(indexA.resolve(INDEX + ".meta")),
			"the original's files must not be touched");
	}

	@Test
	void boundEmptyDirectoryIsRebuiltFromTheStoredVectors() throws Exception
	{
		final Path storage = this.temp.resolve("storage");
		final Path empty   = this.temp.resolve("empty");
		IndexLocations.bind(LOCATION, this.temp.resolve("index"));
		createStore(storage, onDisk(IndexLocation.Named(LOCATION)));

		IndexLocations.bind(LOCATION, empty);
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final VectorIndex<Doc> vectorIndex = vectorIndex(manager.root());
			assertFalse(isIncrementalMode(vectorIndex), "no graph there: built from the stored vectors");
			assertEquals(5, vectorIndex.search(queryVector(), 5).size());
			vectorIndex.persistToDisk();
		}
		assertTrue(Files.exists(empty.resolve(INDEX + ".meta")), "the rebuilt graph is written to the bound directory");
	}

	@Test
	void unboundNameFailsTheLoadNamingIndexAndLocation() throws Exception
	{
		final Path storage = this.temp.resolve("storage");
		IndexLocations.bind(LOCATION, this.temp.resolve("index"));
		createStore(storage, onDisk(IndexLocation.Named(LOCATION)));
		IndexLocations.unbind(LOCATION);

		final Throwable failure = assertThrows(Throwable.class, () ->
		{
			try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
			{
				manager.root();
			}
		});
		assertTrue(causeChainContains(failure, "\"" + INDEX + "\"", "\"" + LOCATION + "\""), failure::toString);
	}

	@Test
	void unboundNameFailsTheCreation()
	{
		final VectorIndices<Doc> vectorIndices = GigaMap.<Doc>New().index().register(VectorIndices.Category());
		assertThrows(IllegalStateException.class,
			() -> vectorIndices.add(INDEX, onDisk(IndexLocation.Named(LOCATION)), new DocVectorizer()));
		assertNull(vectorIndices.get(INDEX));
	}

	@Test
	void rebindingDoesNotMoveAnOpenIndex() throws Exception
	{
		final Path storage = this.temp.resolve("storage");
		final Path indexA  = this.temp.resolve("indexA");
		final Path indexB  = this.temp.resolve("indexB");
		IndexLocations.bind(LOCATION, indexA);
		createStore(storage, onDisk(IndexLocation.Named(LOCATION)));

		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Doc> map = manager.root();
			IndexLocations.bind(LOCATION, indexB);
			map.add(new Doc(vector(new Random(5))));
			vectorIndex(map).persistToDisk();
			assertEquals(indexA, vectorIndex(map).indexDirectory());
		}
		assertFalse(Files.exists(indexB));
	}

	@Test
	void lazyLoadedMapResolvesItsLocationWhenItIsLoaded() throws Exception
	{
		final Path storage = this.temp.resolve("storage");
		final Path index   = this.temp.resolve("index");
		IndexLocations.bind(LOCATION, index);
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Doc> map = newMap(onDisk(IndexLocation.Named(LOCATION)));
			manager.setRoot(new Holder(map));
			manager.storeRoot();
			vectorIndex(map).persistToDisk();
		}

		IndexLocations.unbind(LOCATION);
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final Holder holder = manager.root();
			assertFalse(holder.map.isLoaded(), "the start must not have loaded the map");

			final ExecutionException failure = assertThrows(ExecutionException.class,
				() -> CompletableFuture.supplyAsync(() -> holder.map.get()).get());
			assertTrue(causeChainContains(failure, "\"" + LOCATION + "\""), failure::toString);

			IndexLocations.bind(LOCATION, index);
			final int found = CompletableFuture.supplyAsync(
				() -> vectorIndex(holder.map.get()).search(queryVector(), 5).size()
			).get();
			assertEquals(5, found);
		}
	}

	@Test
	void inMemoryIndexHasNoIndexDirectory()
	{
		final VectorIndexConfiguration configuration = VectorIndexConfiguration.builder()
			.dimension(DIMENSION)
			.indexDirectory(this.temp.resolve("unused"))
			.build();
		final VectorIndex<Doc> vectorIndex = GigaMap.<Doc>New().index()
			.register(VectorIndices.Category())
			.add(INDEX, configuration, new DocVectorizer());
		try
		{
			assertNull(vectorIndex.indexDirectory());
		}
		finally
		{
			vectorIndex.close();
		}
	}

	@Test
	void locationIsStoredAsAPlainNameField()
	{
		// a new stored class would make older library versions fail to start; a new field does not
		final Path storage = this.temp.resolve("storage");
		IndexLocations.bind(LOCATION, this.temp.resolve("index"));
		createStore(storage, onDisk(IndexLocation.Named(LOCATION)));

		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final PersistenceTypeDictionary dictionary    = manager.persistenceManager().typeDictionary();
			final PersistenceTypeDefinition configuration = dictionary.lookupTypeByName(VectorIndexConfiguration.Default.class.getName());
			assertTrue(
				configuration.allMembers().containsSearched(member ->
					"indexLocationName".equals(member.name()) && String.class.getName().equals(member.typeName())
				),
				configuration::toString
			);
			assertNull(dictionary.lookupTypeByName(IndexLocation.Absolute.class.getName()));
			assertNull(dictionary.lookupTypeByName(IndexLocation.Named.class.getName()));

			final PersistenceTypeDefinition index = dictionary.lookupTypeByName(VectorIndex.Default.class.getName());
			assertFalse(
				index.allMembers().containsSearched(member -> "resolvedDirectory".equals(member.name())),
				"the resolved directory must not be stored"
			);
		}
	}

	@Test
	void ensureKeepsTheStoredLocation()
	{
		final Path storage = this.temp.resolve("storage");
		final Path index   = this.temp.resolve("index");
		createStore(storage, onDisk(IndexLocation.Absolute(index)));

		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Doc>     map         = manager.root();
			final VectorIndex<Doc> vectorIndex = map.index().get(VectorIndices.class).ensure(
				INDEX,
				onDisk(IndexLocation.Absolute(this.temp.resolve("other"))),
				new DocVectorizer()
			);
			assertEquals(IndexLocation.Absolute(index), vectorIndex.configuration().indexLocation());
			assertEquals(index, vectorIndex.indexDirectory());
		}
	}


	///////////////////////////////////////////////////////////////////////////
	// changeIndexLocation //
	////////////////////////

	@Test
	void changeIndexLocationTakesEffectFromTheNextStartWithoutRebuild() throws Exception
	{
		final Path storage = this.temp.resolve("storage");
		final Path index   = this.temp.resolve("index");
		createStore(storage, onDisk(IndexLocation.Absolute(index)));

		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Doc> map = manager.root();
			map.index().get(VectorIndices.class).changeIndexLocation(INDEX, IndexLocation.Named(LOCATION));
			assertEquals(LOCATION, vectorIndex(map).configuration().indexLocationName());
			assertEquals(index, vectorIndex(map).indexDirectory(), "the running index stays where it is");
			assertEquals(index, vectorIndex(map).configuration().indexDirectory(),
				"the previous directory is kept for older library versions");
			map.store();
		}

		// migration without a rebuild: bind the name to the current directory
		IndexLocations.bind(LOCATION, index);
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final VectorIndex<Doc> vectorIndex = vectorIndex(manager.root());
			assertEquals(LOCATION, vectorIndex.configuration().indexLocationName(), "the change must reach the storage");
			assertEquals(index, vectorIndex.indexDirectory());
			assertTrue(isIncrementalMode(vectorIndex), "the graph must be loaded, not rebuilt");
			assertEquals(5, vectorIndex.search(queryVector(), 5).size());
		}
	}

	@Test
	void namedIndexUsesTheBoundDirectoryNotTheKeptOne() throws Exception
	{
		final Path storage = this.temp.resolve("storage");
		final Path index   = this.temp.resolve("index");
		final Path bound   = this.temp.resolve("bound");
		createStore(storage, onDisk(IndexLocation.Absolute(index)));
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Doc> map = manager.root();
			map.index().get(VectorIndices.class).changeIndexLocation(INDEX, IndexLocation.Named(LOCATION));
			map.store();
		}

		copyTree(index, bound);
		IndexLocations.bind(LOCATION, bound);
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final VectorIndex<Doc> vectorIndex = vectorIndex(manager.root());
			assertEquals(index, vectorIndex.configuration().indexDirectory());
			assertEquals(bound, vectorIndex.indexDirectory());
			assertTrue(isIncrementalMode(vectorIndex), "the copied graph must be loaded, not rebuilt");
		}
	}

	@Test
	void changeIndexLocationToTheCurrentLocationChangesNothing()
	{
		final GigaMap<Doc>     map         = newMap(onDisk(IndexLocation.Absolute(this.temp.resolve("index"))));
		final VectorIndex<Doc> vectorIndex = vectorIndex(map);
		final VectorIndexConfiguration before = vectorIndex.configuration();
		try
		{
			map.index().get(VectorIndices.class).changeIndexLocation(INDEX, before.indexLocation());
			assertSame(before, vectorIndex.configuration());
		}
		finally
		{
			vectorIndex.close();
		}
	}

	@Test
	void changeIndexLocationRejectsWhatItCannotChange()
	{
		final Path storage = this.temp.resolve("storage");
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Doc> map = GigaMap.New();
			manager.setRoot(map);
			final VectorIndices<Doc> vectorIndices = map.index().register(VectorIndices.Category());
			vectorIndices.add("memory", VectorIndexConfiguration.builder().dimension(DIMENSION).build(), new DocVectorizer());
			vectorIndices.add(INDEX, onDisk(IndexLocation.Absolute(this.temp.resolve("index"))), new DocVectorizer());
			manager.storeRoot();

			final IndexLocation named = IndexLocation.Named(LOCATION);
			assertThrows(IllegalArgumentException.class, () -> vectorIndices.changeIndexLocation("missing", named));
			assertThrows(IllegalStateException.class   , () -> vectorIndices.changeIndexLocation("memory" , named));
			map.markReadOnly();
			try
			{
				assertThrows(IllegalStateException.class, () -> vectorIndices.changeIndexLocation(INDEX, named));
			}
			finally
			{
				map.unmarkReadOnly();
			}
			assertNull(vectorIndices.get(INDEX).configuration().indexLocationName());
		}
	}


	///////////////////////////////////////////////////////////////////////////
	// configuration //
	//////////////////

	@Test
	void builderKeepsEitherADirectoryOrAName()
	{
		final VectorIndexConfiguration named = VectorIndexConfiguration.builder()
			.dimension(DIMENSION)
			.onDisk(true)
			.indexDirectory(this.temp.resolve("unused"))
			.indexLocation(IndexLocation.Named(LOCATION))
			.build();
		assertEquals(LOCATION, named.indexLocationName());
		assertNull(named.indexDirectory(), "a new named index must not carry an unused path");

		final VectorIndexConfiguration absolute = VectorIndexConfiguration.builder()
			.dimension(DIMENSION)
			.onDisk(true)
			.indexLocation(IndexLocation.Named(LOCATION))
			.indexDirectory(this.temp.resolve("index"))
			.build();
		assertNull(absolute.indexLocationName());
		assertEquals(IndexLocation.Absolute(this.temp.resolve("index")), absolute.indexLocation());
	}

	@Test
	void withIndexLocationChangesOnlyTheLocation()
	{
		final Path                     index  = this.temp.resolve("index");
		final VectorIndexConfiguration source = VectorIndexConfiguration.builder()
			.dimension(DIMENSION)
			.similarityFunction(VectorSimilarityFunction.DOT_PRODUCT)
			.maxDegree(24)
			.beamWidth(120)
			.onDisk(true)
			.indexDirectory(index)
			.approximateScoring(ApproximateScoring.FUSED_PQ)
			.parallelOnDiskWrite(true)
			.build();

		final VectorIndexConfiguration named = source.withIndexLocation(IndexLocation.Named(LOCATION));
		assertEquals(LOCATION, named.indexLocationName());
		assertEquals(index, named.indexDirectory(), "the previous directory is kept for older library versions");
		assertSameSettings(source, named);

		final VectorIndexConfiguration moved = named.withIndexLocation(IndexLocation.Absolute(this.temp.resolve("other")));
		assertNull(moved.indexLocationName());
		assertEquals(this.temp.resolve("other"), moved.indexDirectory());
		assertSameSettings(source, moved);
	}


	///////////////////////////////////////////////////////////////////////////
	// annotation handler //
	///////////////////////

	@Test
	void annotationHandlerWithANamedLocationSharesTheBoundDirectory()
	{
		final Path bound = this.temp.resolve("bound");
		IndexLocations.bind(LOCATION, bound);
		final GigaMap<Product> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Product.class)
			.register(VectorAnnotationHandler.New(IndexLocation.Named(LOCATION)))
			.generateIndices(map);

		final VectorIndex<Product> vectorIndex = map.index().get(VectorIndices.class).get("embedding");
		try
		{
			assertEquals(IndexLocation.Named(LOCATION), vectorIndex.configuration().indexLocation());
			assertEquals(bound, vectorIndex.indexDirectory(), "files directly in the bound directory");
		}
		finally
		{
			vectorIndex.close();
		}
	}

	@Test
	void annotationHandlerWithAnAbsoluteLocationUsesASubdirectoryPerIndex()
	{
		final Path base = this.temp.resolve("base");
		final GigaMap<Product> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Product.class)
			.register(VectorAnnotationHandler.New(IndexLocation.Absolute(base)))
			.generateIndices(map);

		final VectorIndex<Product> vectorIndex = map.index().get(VectorIndices.class).get("embedding");
		try
		{
			assertEquals(base.resolve("embedding"), vectorIndex.indexDirectory(), "same layout as New(Path)");
		}
		finally
		{
			vectorIndex.close();
		}
	}


	///////////////////////////////////////////////////////////////////////////
	// fixtures //
	/////////////

	static final class Doc
	{
		final float[] embedding;

		Doc(final float[] embedding)
		{
			super();
			this.embedding = embedding;
		}
	}

	static final class DocVectorizer extends Vectorizer<Doc>
	{
		@Override
		public float[] vectorize(final Doc entity)
		{
			return entity.embedding;
		}

		@Override
		public boolean isEmbedded()
		{
			return true;
		}
	}

	static final class Holder
	{
		final Lazy<GigaMap<Doc>> map;

		Holder(final GigaMap<Doc> map)
		{
			super();
			this.map = Lazy.Reference(map);
		}
	}

	static final class Product
	{
		@Vector(dimension = DIMENSION, onDisk = true)
		final float[] embedding;

		Product(final float[] embedding)
		{
			super();
			this.embedding = embedding;
		}
	}

	private static VectorIndexConfiguration onDisk(final IndexLocation location)
	{
		return VectorIndexConfiguration.builder()
			.dimension(DIMENSION)
			.similarityFunction(VectorSimilarityFunction.COSINE)
			.onDisk(true)
			.indexLocation(location)
			.build();
	}

	private static GigaMap<Doc> newMap(final VectorIndexConfiguration configuration)
	{
		final GigaMap<Doc> map = GigaMap.New();
		map.index().register(VectorIndices.Category()).add(INDEX, configuration, new DocVectorizer());
		final Random random = new Random(42);
		for(int i = 0; i < COUNT; i++)
		{
			map.add(new Doc(vector(random)));
		}
		return map;
	}

	private static void createStore(final Path storage, final VectorIndexConfiguration configuration)
	{
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Doc> map = newMap(configuration);
			manager.setRoot(map);
			vectorIndex(map).persistToDisk();
			manager.storeRoot();
		}
	}

	private static VectorIndex<Doc> vectorIndex(final GigaMap<Doc> map)
	{
		return map.index().get(VectorIndices.class).get(INDEX);
	}

	private static float[] vector(final Random random)
	{
		final float[] vector = new float[DIMENSION];
		for(int i = 0; i < DIMENSION; i++)
		{
			vector[i] = random.nextFloat() * 2 - 1;
		}
		return vector;
	}

	private static float[] queryVector()
	{
		return vector(new Random(999));
	}

	private static void assertSameSettings(final VectorIndexConfiguration expected, final VectorIndexConfiguration actual)
	{
		assertEquals(expected.dimension()          , actual.dimension()          );
		assertEquals(expected.similarityFunction() , actual.similarityFunction() );
		assertEquals(expected.maxDegree()          , actual.maxDegree()          );
		assertEquals(expected.beamWidth()          , actual.beamWidth()          );
		assertEquals(expected.onDisk()             , actual.onDisk()             );
		assertEquals(expected.vectorStorage()      , actual.vectorStorage()      );
		assertEquals(expected.approximateScoring() , actual.approximateScoring() );
		assertEquals(expected.enablePqCompression(), actual.enablePqCompression());
		assertEquals(expected.parallelOnDiskWrite(), actual.parallelOnDiskWrite());
	}

	/**
	 * Reads the private incremental-mode flag of {@link VectorIndex.Default}: it tells a loaded graph
	 * from a rebuilt one, which search results alone cannot.
	 */
	private static boolean isIncrementalMode(final VectorIndex<?> index) throws ReflectiveOperationException
	{
		final Field field = VectorIndex.Default.class.getDeclaredField("incrementalMode");
		field.setAccessible(true);
		return field.getBoolean(index);
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
