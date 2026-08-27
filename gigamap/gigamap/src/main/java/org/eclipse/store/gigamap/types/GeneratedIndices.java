package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
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

import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.collections.types.XGettingTable;
import org.eclipse.serializer.typing.KeyValue;

import static org.eclipse.serializer.util.X.notNull;

/**
 * A typed, by-name handle to the bitmap indexers produced by an {@link IndexerGenerator} run.
 * <p>
 * Annotation-generated indexers are otherwise only reachable through the {@link BitmapIndices} getter API,
 * where the caller must know the exact generated type to pick the right getter. This handle holds the
 * generated indexer instances keyed by their index name, so the result of a generation call can be kept and
 * reused much like a hand-written {@code public static final} indexer constant:
 * <pre>{@code
 * GeneratedIndices<Person> idx = IndexerGenerator.AnnotationBased(Person.class).generateIndices(map);
 * map.query(idx.getIndexerString("firstName").startsWith("J"));
 * map.query(idx.getSpatialIndexer("location").near(52.5, 13.4, 100));
 * }</pre>
 * The held instances query correctly because conditions resolve against the registered index by name — the
 * same reason hand-written constants keep working across reloads. For a reloaded {@link GigaMap}, simply
 * re-run the (idempotent) generation to obtain a fresh handle.
 *
 * @param <E> the entity type
 *
 * @see IndexerGenerator
 */
public interface GeneratedIndices<E>
{
	/**
	 * Returns the generated indexer registered under the given name, or {@code null} if there is none.
	 *
	 * @param name the index name
	 * @return the generated indexer, or {@code null}
	 */
	public Indexer<E, ?> get(String name);

	/**
	 * Returns the generated indexer registered under the given name, cast to the requested indexer type.
	 *
	 * @param <I>         the expected indexer type
	 * @param indexerType the expected indexer type
	 * @param name        the index name
	 * @return the generated indexer cast to {@code I}, or {@code null} if there is none
	 * @throws ClassCastException if the generated indexer is not of the requested type
	 */
	public <I extends Indexer<E, ?>> I get(Class<I> indexerType, String name);

	/**
	 * Returns an immutable view of all generated indexers keyed by index name.
	 *
	 * @return all generated indexers by name
	 */
	public XGettingTable<String, ? extends Indexer<E, ?>> all();


	// -- typed convenience mirroring the BitmapIndices getter surface (same method names) --

	/** @return the generated {@link Indexer} with the given key type, or {@code null} */
	@SuppressWarnings("unchecked")
	public default <K> Indexer<E, K> getIndexerForKey(final Class<K> keyType, final String name)
	{
		return (Indexer<E, K>)this.get(name);
	}

	/** @return the generated {@link IndexerString}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerString<E> getIndexerString(final String name)
	{
		return this.get(IndexerString.class, name);
	}

	/** @return the generated {@link IndexerCharacter}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerCharacter<E> getIndexerCharacter(final String name)
	{
		return this.get(IndexerCharacter.class, name);
	}

	/** @return the generated {@link IndexerBoolean}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerBoolean<E> getIndexerBoolean(final String name)
	{
		return this.get(IndexerBoolean.class, name);
	}

	/** @return the generated {@link IndexerByte}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerByte<E> getIndexerByte(final String name)
	{
		return this.get(IndexerByte.class, name);
	}

	/** @return the generated {@link IndexerShort}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerShort<E> getIndexerShort(final String name)
	{
		return this.get(IndexerShort.class, name);
	}

	/** @return the generated {@link IndexerInteger}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerInteger<E> getIndexerInteger(final String name)
	{
		return this.get(IndexerInteger.class, name);
	}

	/** @return the generated {@link IndexerLong}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerLong<E> getIndexerLong(final String name)
	{
		return this.get(IndexerLong.class, name);
	}

	/** @return the generated {@link IndexerFloat}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerFloat<E> getIndexerFloat(final String name)
	{
		return this.get(IndexerFloat.class, name);
	}

	/** @return the generated {@link IndexerDouble}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerDouble<E> getIndexerDouble(final String name)
	{
		return this.get(IndexerDouble.class, name);
	}

	/** @return the generated {@link IndexerLocalDate}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerLocalDate<E> getIndexerLocalDate(final String name)
	{
		return this.get(IndexerLocalDate.class, name);
	}

	/** @return the generated {@link IndexerLocalTime}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerLocalTime<E> getIndexerLocalTime(final String name)
	{
		return this.get(IndexerLocalTime.class, name);
	}

	/** @return the generated {@link IndexerLocalDateTime}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerLocalDateTime<E> getIndexerLocalDateTime(final String name)
	{
		return this.get(IndexerLocalDateTime.class, name);
	}

	/** @return the generated {@link IndexerYearMonth}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerYearMonth<E> getIndexerYearMonth(final String name)
	{
		return this.get(IndexerYearMonth.class, name);
	}

	/** @return the generated {@link IndexerInstant}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerInstant<E> getIndexerInstant(final String name)
	{
		return this.get(IndexerInstant.class, name);
	}

	/** @return the generated {@link IndexerZonedDateTime}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default IndexerZonedDateTime<E> getIndexerZonedDateTime(final String name)
	{
		return this.get(IndexerZonedDateTime.class, name);
	}

	/** @return the generated {@link BinaryIndexerUUID}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default BinaryIndexerUUID<E> getIndexerUUID(final String name)
	{
		return this.get(BinaryIndexerUUID.class, name);
	}

	/** @return the generated {@link IndexerMultiValue}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default <K> IndexerMultiValue<E, K> getIndexerMultiValue(final String name)
	{
		return this.get(IndexerMultiValue.class, name);
	}

	/** @return the generated {@link IndexerComparing} (e.g. {@code Comparable} / {@code java.util.Date}), or {@code null} */
	@SuppressWarnings("unchecked")
	public default <K> IndexerComparing<E, K> getIndexerComparing(final Class<K> keyType, final String name)
	{
		return this.get(IndexerComparing.class, name);
	}

	/** @return the generated {@link SpatialIndexer}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default SpatialIndexer<E> getSpatialIndexer(final String name)
	{
		return this.get(SpatialIndexer.class, name);
	}

	/** @return the generated numeric {@link BinaryIndexer} ({@code @Index(binary = true)} / {@code kind = BINARY}); for binary {@code String} use {@link #getBinaryIndexerString(String)}, for {@code UUID} use {@link #getIndexerUUID(String)}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default BinaryIndexer<E> getBinaryIndexer(final String name)
	{
		return this.get(BinaryIndexer.class, name);
	}

	/** @return the generated binary {@link BinaryIndexerString} ({@code @Index(binary = true)} {@code String}; neither an {@code IndexerString} nor a {@code BinaryIndexer}), or {@code null} */
	@SuppressWarnings("unchecked")
	public default BinaryIndexerString<E> getBinaryIndexerString(final String name)
	{
		return this.get(BinaryIndexerString.class, name);
	}

	/** @return the generated byte-sliced {@link ByteIndexerNumber} ({@code kind = BIT_SLICED}), or {@code null} */
	@SuppressWarnings("unchecked")
	public default <K extends Number> ByteIndexerNumber<E, K> getByteIndexerNumber(final Class<K> keyType, final String name)
	{
		return this.get(ByteIndexerNumber.class, name);
	}

	/** @return the generated byte-sliced {@link ByteIndexerInstant}, or {@code null} */
	@SuppressWarnings("unchecked")
	public default ByteIndexerInstant<E> getByteIndexerInstant(final String name)
	{
		return this.get(ByteIndexerInstant.class, name);
	}


	/**
	 * Creates a {@link GeneratedIndices} handle backed by the given name-to-indexer table.
	 *
	 * @param <E>     the entity type
	 * @param indices the generated indexers keyed by name
	 * @return a new handle
	 */
	public static <E> GeneratedIndices<E> New(final XGettingTable<String, ? extends Indexer<E, ?>> indices)
	{
		notNull(indices);
		final EqHashTable<String, Indexer<E, ?>> copy = EqHashTable.New();
		for(final KeyValue<String, ? extends Indexer<E, ?>> kv : indices)
		{
			copy.add(kv.key(), kv.value());
		}
		return new Default<>(copy);
	}


	public final class Default<E> implements GeneratedIndices<E>
	{
		private final EqHashTable<String, Indexer<E, ?>> indices;

		Default(final EqHashTable<String, Indexer<E, ?>> indices)
		{
			super();
			this.indices = indices;
		}

		@Override
		public Indexer<E, ?> get(final String name)
		{
			return this.indices.get(name);
		}

		@Override
		public <I extends Indexer<E, ?>> I get(final Class<I> indexerType, final String name)
		{
			final Indexer<E, ?> indexer = this.indices.get(name);
			return indexer == null ? null : indexerType.cast(indexer);
		}

		@Override
		public XGettingTable<String, ? extends Indexer<E, ?>> all()
		{
			// immutable snapshot: callers must not be able to mutate the backing table (not even via downcast).
			return this.indices.immure();
		}
	}

}
