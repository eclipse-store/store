package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.exceptions.BitmapIndicesException;
import org.eclipse.store.gigamap.exceptions.UniqueConstraintViolationExceptionBitmap;
import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.ConstHashEnum;
import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.collections.types.XGettingCollection;
import org.eclipse.serializer.collections.types.XGettingEnum;
import org.eclipse.serializer.collections.types.XGettingTable;
import org.eclipse.serializer.collections.types.XImmutableEnum;
import org.eclipse.serializer.collections.types.XIterable;
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.serializer.util.X;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Mutable bitmap index registry and manager.
 *
 * @param <E> the entity type
 */
public interface BitmapIndices<E>
extends
IndexGroup.Internal<E>,
UniqueConstraints<E>,
XIterable<BitmapIndex<E, ?>>,
Iterable<KeyValue<String, ? extends BitmapIndex<E, ?>>>
{
	/**
	 * Adds an {@link Indexer} to this group.
	 * <p>
	 * The indexer's {@link Indexer#name() name()} is used as its unique registry key
	 * within this {@code BitmapIndices}. Registering a second indexer under a name
	 * that is already taken fails fast with a {@link RuntimeException} rather than
	 * silently overwriting the existing one. This is mostly invisible when names come
	 * from the default derivation (declaration-based names are naturally distinct),
	 * but it can bite when {@link Indexer#name() name()} is overridden manually or
	 * when anonymous {@link Indexer} instances produce colliding default names; give
	 * each indexer an explicit, unique name in those cases.
	 * <p>
	 * On a parent {@link GigaMap} that already contains entities, the new index is back-filled from them,
	 * so that it answers queries about the entities added before it as well.
	 * <p>
	 * <b>Behavior on failure:</b> the index is built completely before it is registered, so if
	 * {@code indexer} throws for one of the already-present entities, nothing is registered and this group
	 * is left exactly as it was: no index is queryable under the given name, no sibling index is affected,
	 * and nothing of the attempt is persisted by a subsequent {@code store()}. The indexer's exception is
	 * the one propagated - should discarding the half-built index fail in turn, that secondary failure is
	 * attached to it as a suppressed exception. The name stays free, so the call can simply be repeated
	 * with corrected logic.
	 *
	 * @param <K> the key type
	 * @param indexer the indexing logic
	 * @return the resuling index
	 * @throws IllegalArgumentException if {@code indexer} or its name is {@code null}
	 * @throws RuntimeException if an indexer with the same name is already registered
	 * @see Indexer#name()
	 */
	public <K> BitmapIndex<E, K> add(final Indexer<? super E, K> indexer);

	/**
	 * Adds an {@link Indexer} to this group <b>without initializing it from the entities that are
	 * already present</b> in the parent {@link GigaMap}.
	 * <p>
	 * Unlike {@link #add(Indexer)}, the newly registered index is <b>not</b> back-filled by
	 * iterating the existing entities: it starts empty and only receives entries for entities that
	 * are added or updated <i>after</i> this call.
	 * <p>
	 * <b>Advanced use — the caller is responsible for correctness.</b> This is only safe when it is
	 * guaranteed that no entity currently present in the map would be indexed under a non-trivial
	 * key by {@code indexer} — i.e. the index data would be empty even if it were back-filled. The
	 * canonical case is a freshly introduced key that, by construction, no existing entity can carry
	 * yet (e.g. a per-value index created the first time that value appears). Using this for an
	 * indexer that <i>would</i> match already-present entities leaves the index inconsistent and
	 * produces wrong query results. When in doubt, use {@link #add(Indexer)}.
	 * <p>
	 * Since no back-fill runs, {@link Indexer#index(Object)} is never called for an already-present entity,
	 * so the failure mode {@link #add(Indexer)} documents - a partially built index - cannot occur here.
	 *
	 * @param <K> the key type
	 * @param indexer the indexing logic
	 * @return the resulting, initially empty index
	 * @throws IllegalArgumentException if {@code indexer} or its name is {@code null}
	 * @throws RuntimeException if an indexer with the same name is already registered
	 * @see #add(Indexer)
	 */
	public <K> BitmapIndex<E, K> addWithoutInitialization(final Indexer<? super E, K> indexer);

	/**
	 * Adds all indexers to this group.
	 * <p>
	 * For details see {@link #add(Indexer)}.
	 *
	 * @param indexers the new indexing logic
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public default BitmapIndices<E> addAll(final Indexer<? super E, ?>... indexers)
	{
		return this.addAll(X.List(indexers));
	}
	
	/**
	 * Adds all indexers to this group.
	 * <p>
	 * For details see {@link #add(Indexer)}.
	 * <p>
	 * The passed iterable is traversed exactly once, so a single-use iterable (e.g. stream-backed) is fine.
	 * Passing no indexer at all is a no-op. Two indexers with the same {@link Indexer#name() name} within the
	 * same call are rejected, just like an indexer whose name is already registered.
	 * <p>
	 * The back-fill over the already-present entities is a single pass for the whole batch, so every entity -
	 * and, with lazily loaded segments, every segment - is visited once rather than once per indexer. The
	 * indices are independent of each other, so the only difference to filling them one after another is the
	 * order in which the indexers see the entities.
	 * <p>
	 * <b>Behavior on failure:</b> all-or-nothing across the batch. Every index is built completely before any
	 * of them is registered, so an indexer throwing for one of the already-present entities registers
	 * <b>no</b> index of the batch - not even those whose own logic never threw - and leaves this group
	 * exactly as it was. For the per-index details see {@link #add(Indexer)}.
	 *
	 * @param indexers the new indexing logic
	 * @return this
	 */
	public BitmapIndices<E> addAll(Iterable<? extends Indexer<? super E, ?>> indexers);
	
	/**
	 * Removes the index registered under the given name.
	 * <p>
	 * The index and its data are dropped from this group. If the index is also registered as a
	 * unique constraint, that constraint is lifted as well. The index' off-heap memory is freed
	 * synchronously; the orphaned data <i>on disk</i> is reclaimed by the storage's garbage collection
	 * on the next housekeeping cycle after the surrounding {@link GigaMap} is stored.
	 * <p>
	 * Registering an index under the same name again via {@link #add(Indexer)} or {@link #ensure(Indexer)}
	 * does <b>not</b> restore the lifted unique constraint, nor identity-index membership: the re-added
	 * index is a plain index that accepts duplicates. To change the logic of an index while preserving both
	 * memberships, use {@link #update(Indexer)} instead of removing and re-adding it.
	 * <p>
	 * Removing an index that is currently registered as an
	 * {@link #identityIndices() identity index} is rejected with a {@link RuntimeException},
	 * because doing so would silently break entity lookup and removal; update the identity indices
	 * first via {@link #setIdentityIndices(org.eclipse.serializer.collections.types.XGettingEnum)}.
	 *
	 * @param name the name of the index to remove
	 * @return {@code true} if an index with that name existed and was removed, {@code false} otherwise
	 * @throws RuntimeException if the index is currently registered as an identity index, or if the
	 *         parent {@link GigaMap} is read-only
	 * @see #update(Indexer)
	 */
	public boolean removeIndex(String name);

	/**
	 * Removes the index registered under the {@link IndexIdentifier#name() name} of the given identifier.
	 * <p>
	 * For details see {@link #removeIndex(String)}.
	 *
	 * @param identifier the identifier whose name identifies the index to remove
	 * @return {@code true} if an index with that name existed and was removed, {@code false} otherwise
	 */
	public default boolean removeIndex(final IndexIdentifier<? super E, ?> identifier)
	{
		return this.removeIndex(identifier.name());
	}

	/**
	 * Replaces the indexing logic of the already-registered index that has the same
	 * {@link Indexer#name() name} as the given indexer, and rebuilds that index's data from all
	 * entities currently contained in the parent {@link GigaMap}.
	 * <p>
	 * Unlike {@link #ensure(Indexer)}, which never changes an existing index, this method is the
	 * supported way to change an index's logic. Unique-constraint and identity-index membership of
	 * the existing index are preserved: if the index was a unique constraint, the rebuild
	 * re-validates uniqueness under the new logic (and fails with a
	 * {@link org.eclipse.store.gigamap.exceptions.UniqueConstraintViolationException} if the current
	 * data is no longer unique); if it was an identity index, the identity set is re-pointed to the
	 * rebuilt index.
	 * <p>
	 * <b>Behavior on failure:</b> atomic, for a unique constraint and a plain index alike. The replacement
	 * index is built from all current entities - and, for a unique constraint, validated - <b>before</b> the
	 * existing index is dropped, so a uniqueness violation or an {@code indexer} throwing for one of the
	 * entities leaves the existing index registered, fully populated and still enforcing its unique and
	 * identity membership. Nothing of the attempt is persisted by a subsequent {@code store()}, and the call
	 * can simply be repeated with corrected logic.
	 * <p>
	 * The price of that atomicity is that the old and the new data of this one index are held simultaneously
	 * for the duration of the rebuild, which roughly doubles that index' peak footprint - noticeable for a
	 * high-cardinality index over a large map.
	 *
	 * @param <K> the key type
	 * @param indexer the new indexing logic, whose name selects the index to replace
	 * @return the rebuilt index
	 * @throws RuntimeException if no index is registered under the indexer's name, or if the parent
	 *         {@link GigaMap} is read-only
	 * @see #removeIndex(String)
	 */
	public <K> BitmapIndex<E, K> update(Indexer<? super E, K> indexer);

	/**
	 * Ensures that an index exists in this group.
	 * <p>
	 * This is a get-or-create operation: if an index is already registered under the given
	 * indexer's {@link Indexer#name() name} (and key type), that existing index is returned
	 * <b>unchanged</b> and the passed {@code indexer}'s logic is ignored. {@code ensure} therefore
	 * never modifies an existing index. To change the logic of an already-registered index, use
	 * {@link #update(Indexer)}.
	 * <p>
	 * When it does create an index, it does so exactly like {@link #add(Indexer)}, including that method's
	 * back-fill and its behavior on failure.
	 *
	 * @param <K> the key type
	 * @param indexer the indexing logic
	 * @return the resuling index
	 */
	public <K> BitmapIndex<E, K> ensure(final Indexer<? super E, K> indexer);

	/**
	 * Ensures that indexes exist in this group.
	 * <p>
	 * For details see {@link #ensure(Indexer)}
	 *
	 * @param indexers the indexing logic
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public default BitmapIndices<E> ensureAll(final Indexer<? super E, ?>... indexers)
	{
		for(final Indexer<? super E, ?> indexer : indexers)
		{
			this.ensure(indexer);
		}
		
		return this;
	}
	
	/**
	 * Ensures that indexes exist in this group.
	 * <p>
	 * For details see {@link #ensure(Indexer)}
	 *
	 * @param indexers the indexing logic
	 * @return this
	 */
	public default BitmapIndices<E> ensureAll(final Iterable<? extends Indexer<? super E, ?>> indexers)
	{
		for(final Indexer<? super E, ?> indexer : indexers)
		{
			this.ensure(indexer);
		}
		
		return this;
	}
	
	/**
	 * Ensures that all indices are optimized for small memory usage.
	 *
	 * @see #ensureOptimizedPerformance()
	 */
	public void ensureOptimizedSize();
	
	/**
	 * Ensures that all indices are optimized for best runtime performance.
	 *
	 * @see #ensureOptimizedSize()
	 */
	public void ensureOptimizedPerformance();
	
	/**
	 * Gets the registered indexing logic with the given name and type, or <code>null</code>.
	 *
	 * @param <K> the key type
	 * @param <I> the indexer type
	 * @param indexerType the type of the indexer to search
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	public <K, I extends Indexer<E, K>> I getIndexer(Class<I> indexerType, String name);
	
	/**
	 * Retrieves an indexer associated with the specified key type and name.
	 *
	 * @param <K> The type of the key used by the indexer.
	 * @param keyType The class type of the key.
	 * @param name the name of the indexer to search
	 * @return The indexer corresponding to the specified key type and name.
	 */
	@SuppressWarnings("unchecked")
	public default <K> Indexer<E, K> getIndexerForKey(final Class<K> keyType, final String name)
	{
		return (Indexer<E, K>)this.getIndexer(Indexer.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerBoolean<E> getIndexerBoolean(final String name)
	{
		return this.getIndexer(IndexerBoolean.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerCharacter<E> getIndexerCharacter(final String name)
	{
		return this.getIndexer(IndexerCharacter.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerByte<E> getIndexerByte(final String name)
	{
		return this.getIndexer(IndexerByte.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerShort<E> getIndexerShort(final String name)
	{
		return this.getIndexer(IndexerShort.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerInteger<E> getIndexerInteger(final String name)
	{
		return this.getIndexer(IndexerInteger.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerLong<E> getIndexerLong(final String name)
	{
		return this.getIndexer(IndexerLong.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerFloat<E> getIndexerFloat(final String name)
	{
		return this.getIndexer(IndexerFloat.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerDouble<E> getIndexerDouble(final String name)
	{
		return this.getIndexer(IndexerDouble.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerString<E> getIndexerString(final String name)
	{
		return this.getIndexer(IndexerString.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerLocalDate<E> getIndexerLocalDate(final String name)
	{
		return this.getIndexer(IndexerLocalDate.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerLocalTime<E> getIndexerLocalTime(final String name)
	{
		return this.getIndexer(IndexerLocalTime.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerLocalDateTime<E> getIndexerLocalDateTime(final String name)
	{
		return this.getIndexer(IndexerLocalDateTime.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerYearMonth<E> getIndexerYearMonth(final String name)
	{
		return this.getIndexer(IndexerYearMonth.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default BinaryIndexerUUID<E> getIndexerUUID(final String name)
	{
		return this.getIndexer(BinaryIndexerUUID.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default <K> IndexerMultiValue<E, K> getIndexerMultiValue(final String name)
	{
		return this.getIndexer(IndexerMultiValue.class, name);
	}

	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerInstant<E> getIndexerInstant(final String name)
	{
		return this.getIndexer(IndexerInstant.class, name);
	}

	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerZonedDateTime<E> getIndexerZonedDateTime(final String name)
	{
		return this.getIndexer(IndexerZonedDateTime.class, name);
	}

	/**
	 * Gets the registered comparing indexer with the given name, or <code>null</code>. Use this for
	 * {@code Comparable} key types (including {@code java.util.Date}) generated for range queries.
	 *
	 * @param <K>     the key type
	 * @param keyType the key type
	 * @param name    the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default <K> IndexerComparing<E, K> getIndexerComparing(final Class<K> keyType, final String name)
	{
		return this.getIndexer(IndexerComparing.class, name);
	}

	/**
	 * Gets the registered spatial indexer with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default SpatialIndexer<E> getSpatialIndexer(final String name)
	{
		return this.getIndexer(SpatialIndexer.class, name);
	}

	/**
	 * Gets the registered binary indexer with the given name, or <code>null</code>.
	 * <p>
	 * Note: the value-typed getters ({@link #getIndexerInteger(String) getIndexerInteger} etc.) match only
	 * the low-cardinality ({@code AUTO}) variant. A binary index ({@code @Index(binary = true)} /
	 * {@code kind = BINARY}) over a <em>numeric</em> type (integral, {@code float}, {@code double}) is a
	 * {@link BinaryIndexer} (a parallel hierarchy, not an {@code IndexerInteger} subtype) and must be
	 * fetched here. Binary indexes over non-numeric types are <em>not</em> {@code BinaryIndexer}s: use
	 * {@link #getBinaryIndexerString(String)} for {@code String} and {@link #getIndexerUUID(String)} for
	 * {@code UUID}.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default BinaryIndexer<E> getBinaryIndexer(final String name)
	{
		return this.getIndexer(BinaryIndexer.class, name);
	}

	/**
	 * Gets the registered binary {@code String} indexer with the given name, or <code>null</code>.
	 * <p>
	 * Note: a binary {@code String} index ({@code @Index(binary = true)} / {@code kind = BINARY}) is a
	 * {@link BinaryIndexerString} (a {@link BinaryCompositeIndexer}, neither an {@link IndexerString} nor a
	 * {@link BinaryIndexer}) and must be fetched here rather than via {@link #getIndexerString(String)} or
	 * {@link #getBinaryIndexer(String)}.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default BinaryIndexerString<E> getBinaryIndexerString(final String name)
	{
		return this.getIndexer(BinaryIndexerString.class, name);
	}

	/**
	 * Gets the registered byte-sliced numeric indexer with the given name, or <code>null</code>.
	 * <p>
	 * Note: an index generated with {@code kind = BIT_SLICED} is a {@link ByteIndexerNumber} (a parallel
	 * hierarchy, not an {@code IndexerInteger} subtype) and must be fetched here rather than via the
	 * value-typed getters.
	 *
	 * @param <K>     the numeric key type
	 * @param keyType the numeric key type
	 * @param name    the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default <K extends Number> ByteIndexerNumber<E, K> getByteIndexerNumber(final Class<K> keyType, final String name)
	{
		return this.getIndexer(ByteIndexerNumber.class, name);
	}

	/**
	 * Gets the registered byte-sliced {@code Instant} indexer with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default ByteIndexerInstant<E> getByteIndexerInstant(final String name)
	{
		return this.getIndexer(ByteIndexerInstant.class, name);
	}

	/**
	 * Gets the registered String index with given name, or <code>null</code>.
	 * <p>
	 * This is a shortcut for <code>get(String.class, name);</code>.
	 *
	 * @param name the name of the index to search
	 * @return the found index or <code>null</code>
	 */
	public default BitmapIndex<E, String> get(final String name)
	{
		return this.get(String.class, name);
	}

	/**
	 * Gets the registered index with given key type and name, or <code>null</code>.
	 *
	 * @param <K> the key type
	 * @param keyType the key type of the index to search
	 * @param name the name of the index to search
	 * @return the found index or <code>null</code>
	 */
	public <K> BitmapIndex<E, K> get(Class<K> keyType, String name);
	
	
	public interface Internal<E> extends BitmapIndices<E>
	{
		public <K> BitmapIndex.Internal<E, K> internalGet(Class<K> keyType, String indexName);
	}
	
	/**
	 * Get the registered identity indices used to identify entities during lookup and removal operations.
	 * <p>
	 * Note: Identity indices do not enforce uniqueness. To ensure that identity values are unique
	 * across all entities, add a unique constraint via {@link #addUniqueConstraint(Indexer)}.
	 *
	 * @return all registered identity indices, might be <code>null</code>
	 */
	public XImmutableEnum<? extends BitmapIndex<E, ?>> identityIndices();

	/**
	 * Sets the identity indices used to identify entities during lookup and removal operations.
	 * <p>
	 * Identity indices define which fields are used to build internal queries for looking up
	 * and removing entities. They do not enforce uniqueness. To ensure that no two entities
	 * share the same identity value, add a unique constraint via {@link #addUniqueConstraint(Indexer)}.
	 *
	 * @param identityIndices the new, non-empty, identity indices
	 * @return this
	 */
	public BitmapIndices<E> setIdentityIndices(XGettingEnum<? extends IndexIdentifier<? super E, ?>> identityIndices);

	/**
	 * Sets the identity indices used to identify entities during lookup and removal operations.
	 * <p>
	 * Identity indices define which fields are used to build internal queries for looking up
	 * and removing entities. They do not enforce uniqueness. To ensure that no two entities
	 * share the same identity value, add a unique constraint via {@link #addUniqueConstraint(Indexer)}.
	 *
	 * @param identityIndices the new, non-empty, identity indices
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public default <I extends IndexIdentifier<? super E, ?>> BitmapIndices<E> setIdentityIndices(final I... identityIndices)
	{
		return this.setIdentityIndices(X.Enum(identityIndices));
	}
	
	public XImmutableEnum<? extends BitmapIndex<E, ?>> uniqueConstraints();
	
	/**
	 * Creates statistics of this index group for debugging or analyzing purposes.
	 *
	 * @return the statistics of this index group
	 */
	public Statistics<E> createStatistics();
	
	public void accessIndices(Consumer<? super XGettingTable<String, ? extends BitmapIndex<E, ?>>> logic);
	
	public void accessUniqueIndices(Consumer<? super XImmutableEnum<? extends BitmapIndex<E, ?>>> logic);
	
	
	public final class Default<E> extends AbstractStateChangeFlagged implements Internal<E>
	{
		static BinaryTypeHandler<Default<?>> provideTypeHandler()
		{
			return BinaryHandlerBitmapIndicesDefault.New();
		}
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final GigaMap.Internal<E> parent;
		
		final EqHashTable<String, BitmapIndex.Internal<E, ?>> bitmapIndices;
		
		XImmutableEnum<? extends BitmapIndex<E, ?>> identityIndices = null;
		
		XImmutableEnum<BitmapIndex.Internal<E, ?>> uniqueConstraints = null;
		
		private transient BitmapIndex.Internal<E, ?>[] cachedIndices           ;
		private transient boolean[]                    cachedIsUniqueIndex     ;
		private transient ChangeHandler[]              cachedPrevChangeHandlers;
		private transient ChangeHandler[]              cachedNewChangeHandlers ;

		/**
		 * Whether the current update already moved at least one entry from the prepared previous state
		 * to the new state. Only relevant between {@link #internalPrepareIndicesUpdate(Object)} and
		 * {@link #internalFinishIndicesUpdate()}: once set, the prepared previous state no longer
		 * describes what is in the indices, so {@link #internalRemovePreparedState(long)} must not use
		 * it any more.
		 */
		private transient boolean                      cachedStateChangeApplied;
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		protected Default(final GigaMap.Internal<E> parent)
		{
			this(parent, EqHashTable.New(), true);
		}
		
		protected Default(
			final GigaMap.Internal<E>                           parent       ,
			final EqHashTable<String, BitmapIndex.Internal<E, ?>> bitmapIndices,
			final boolean                                        stateChanged
		)
		{
			super(stateChanged);
			this.parent        = parent       ;
			this.bitmapIndices = bitmapIndices;
		}
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		private void createChangeHandlers(
			final ChangeHandler[] changeHandlers,
			final E                                      entity
		)
		{
			for(int i = 0; i < this.cachedIndices.length; i++)
			{
				changeHandlers[i] = this.cachedIndices[i].getChangeHandler(entity);
			}
		}
		
		private void clearCachedChangeHandlers()
		{
			for(int i = 0; i < this.cachedPrevChangeHandlers.length; i++)
			{
				this.cachedPrevChangeHandlers[i] = null;
				this.cachedNewChangeHandlers[i]  = null;
			}
			this.cachedStateChangeApplied = false;
		}
		
		@SuppressWarnings("unchecked")
		void rebuildCache()
		{
			final int indexCount = this.bitmapIndices.intSize();
			
			this.cachedIndices            = new BitmapIndex.Internal[indexCount];
			this.cachedIsUniqueIndex      = new boolean[indexCount];
			this.cachedPrevChangeHandlers = new ChangeHandler[indexCount];
			this.cachedNewChangeHandlers  = new ChangeHandler[indexCount];
			
			int i = 0;
			for(final BitmapIndex.Internal<E, ?> index : this.bitmapIndices.values())
			{
				this.cachedIndices[i]       = index;
				this.cachedIsUniqueIndex[i] = this.isUniqueConstraint(index);
				i++;
				// changeHandler arrays stay empty until needed.
			}
		}

		/**
		 * Re-binds each index' indexer to the name the index is registered and persisted under.
		 * <p>
		 * Invoked once after deserialization (see {@code BinaryHandlerBitmapIndicesDefault#complete}).
		 * An anonymous {@link Indexer.Abstract} loses its {@code transient} derived name on reload and
		 * would otherwise recompute a divergent fallback name, breaking index resolution (e.g.
		 * {@link GigaMap#update}, whose lookup condition is built from the indexer via
		 * {@code HashingBitmapIndex#like}).
		 */
		final void restoreIndexerNames()
		{
			// the table key is the name the index is registered under and the name index resolution
			// (BitmapIndices#internalGet) looks up by, so it is the authoritative name to re-bind to.
			for(final KeyValue<String, ? extends BitmapIndex.Internal<E, ?>> entry : this.bitmapIndices)
			{
				final Indexer<? super E, ?> indexer = entry.value().indexer();
				if(indexer instanceof Indexer.Abstract)
				{
					((Indexer.Abstract<?, ?>)indexer).initializeResolvedName(entry.key());
				}
			}
		}
		
		@Override
		public final GigaMap.Internal<E> parentMap()
		{
			return this.parent;
		}
		
		protected final EqHashTable<String, BitmapIndex.Internal<E, ?>> bitmapIndices()
		{
			return this.bitmapIndices;
		}
		
		protected final int indexCount()
		{
			return this.bitmapIndices.intSize();
		}
		
		@Override
		public final synchronized boolean isViolated(final long entityId, final E replacedEntity, final E entity)
		{
			return this.internalCheckViolation(entityId, replacedEntity, entity, false) != null;
		}
		
		@Override
		public final synchronized void check(final long entityId, final E replacedEntity, final E entity)
		{
			final RuntimeException result = this.internalCheckViolation(entityId, replacedEntity, entity, true);
			if(result == null)
			{
				return;
			}
			
			throw result;
		}
		
		/*
		 * While logic-flags are kind of goofy, there really is no other choice, if ...
		 * ... the violated index shall be contained in the exception, if needed.
		 * ... there should not be redundant code for both variants (querying and checking).
		 * ... there should not be unnecessary instantiations (as they are checked potentially for millions of entities)
		 */
		private RuntimeException internalCheckViolation(
			final long    entityId       ,
			final E       replacedEntity ,
			final E       entity         ,
			final boolean createException
		)
		{
			if(this.uniqueConstraints == null)
			{
				return null;
			}
			
			for(final BitmapIndex.Internal<E, ?> index : this.uniqueConstraints)
			{
				if(index.equalKeys(replacedEntity, entity))
				{
					// if old and new entity have equal keys, a replacement cannot be a unique violation.
					continue;
				}

				// A key held only by the entity being updated (e.g. its own stale entry after a class
				// evolution) is not a duplicate; only a different entity holding the key is a violation.
				if(index.internalContains(entity, entityId))
				{
					if(createException)
					{
						throw new UniqueConstraintViolationExceptionBitmap(entityId, replacedEntity, entity, index);
					}
					return X.BREAK();
				}
			}
			
			return null;
		}
		
		@Override
		public final void internalAdd(final long entityId, final E entity)
		{
			// mark in any case: a mid-loop throw from an indexer leaves already updated indices behind.
			try
			{
				for(final BitmapIndex.Internal<E, ?> index : this.bitmapIndices.values())
				{
					index.internalAdd(entityId, entity);
				}
			}
			finally
			{
				this.markStateChangeChildren();
			}
		}

		@Override
		public final void internalAddAll(final long firstEntityId, final Iterable<? extends E> entities)
		{
			try
			{
				for(final BitmapIndex.Internal<E, ?> index : this.bitmapIndices.values())
				{
					index.internalAddAll(firstEntityId, entities);
				}
			}
			finally
			{
				this.markStateChangeChildren();
			}
		}

		@Override
		public final void internalRemove(final long entityId, final E entity)
		{
			/*
			 * Best-effort removal: a throwing indexer must not prevent the remaining indices from
			 * being cleaned up, otherwise the entity (already removed from the map's storage by the
			 * calling context) would leave orphaned entries in perfectly healthy indices. The first
			 * exception is rethrown after all indices had their chance, subsequent failures are
			 * attached as suppressed exceptions.
			 */
			RuntimeException first = null;
			try
			{
				for(final BitmapIndex.Internal<E, ?> index : this.bitmapIndices.values())
				{
					try
					{
						index.internalRemove(entityId, entity);
					}
					catch(final RuntimeException e)
					{
						if(first == null)
						{
							first = e;
						}
						else
						{
							first.addSuppressed(e);
						}
					}
				}
			}
			finally
			{
				this.markStateChangeChildren();
			}
			if(first != null)
			{
				throw first;
			}
		}
		
		@Override
		public void internalRemoveAll()
		{
			for(final BitmapIndex.Internal<E, ?> index : this.bitmapIndices.values())
			{
				index.internalRemoveAll();
			}
			this.markStateChangeChildren();
		}

		/**
		 * Rebuilds all bitmap indices from the current entity state, one index at a time, and - unlike the
		 * generic {@link IndexGroup.Internal#internalReindex(GigaMap) clear + re-add} default - both
		 * re-validates the registered unique constraints and survives a failing {@link Indexer}.
		 * <p>
		 * Each index is rebuilt into a replacement built <b>aside</b> and swapped in only once its data is
		 * complete, the way {@link #update(Indexer)} redefines one. The generic default drops every index
		 * first and re-adds entity by entity through {@link #internalAdd(long, Object)}, which fans out to
		 * all of them - so a single indexer throwing at entity <i>k</i> would truncate the whole group,
		 * healthy indices included, to the entities before <i>k</i>. Here a throwing indexer costs only its
		 * own index' rebuild: that index is left exactly as it was - as stale as before the call, but
		 * complete - every other index is rebuilt, and none is ever a prefix of the entities. The first
		 * failure is rethrown once all indices have been attempted, the rest attached as suppressed.
		 * <p>
		 * A <b>unique-constraint violation is not such a failure</b>: the rebuild completed and merely
		 * produced data that collides, so the replacement is swapped in and the violation reported
		 * afterwards. The indices then describe the entities as they actually are, which is what the
		 * documented repair - re-distinguish the colliding keys via {@code update}/{@code apply}, then
		 * {@code reindex()} again - operates on. Reporting before the swap would keep exactly the stale keys
		 * that made the rebuild necessary in the first place.
		 * <p>
		 * The price of rebuilding one index at a time is one pass over the entities per index, rather than
		 * one in total. That bounds the additional memory to a single index' data - the same peak
		 * {@link #update(Indexer)} already has - instead of duplicating the whole group.
		 */
		@Override
		public final void internalReindex(final GigaMap<E> parentMap)
		{
			// Snapshot: the loop swaps entries, which mutates the table it would otherwise iterate.
			// Only registered indices are rebuilt - a composite's sub-indices are its own children and
			// never appear here, which matters because their indexer() is the sub-index itself.
			final BulkList<BitmapIndex.Internal<E, ?>> indices = BulkList.New(this.bitmapIndices.values());
			if(indices.isEmpty())
			{
				return;
			}

			Throwable first = null;
			try
			{
				for(final BitmapIndex.Internal<E, ?> existing : indices)
				{
					try
					{
						this.reindexSingleIndex(existing);
					}
					catch(final RuntimeException e)
					{
						first = addAsFailure(first, e);
					}
				}
			}
			finally
			{
				// entries were replaced, so the transient lookup arrays no longer describe the table
				this.rebuildCache();
			}

			if(first != null)
			{
				throw (RuntimeException)first;
			}
		}

		/**
		 * Rebuilds a single registered index into a replacement and swaps it in. Deliberately does not
		 * consult {@link #ensureMutable(String)}: {@code GigaMap.reindex()} has already checked, and that
		 * guard may release the parent-map monitor while waiting, which would expose a half-swapped group.
		 *
		 * @param existing the registered index to rebuild
		 */
		private void reindexSingleIndex(final BitmapIndex.Internal<E, ?> existing)
		{
			// captured before the swap: #internalRemoveIndex strips both memberships
			final boolean wasUnique   = this.isUniqueConstraint(existing);
			final boolean wasIdentity = this.isIdentityIndex(existing);

			final BitmapIndex.Internal<E, ?> replacement = existing.indexer().createFor(this);

			final UniquenessViolation<E> violation;
			try
			{
				this.validateIndexParent(replacement);
				if(!existing.name().equals(replacement.name()))
				{
					throw new BitmapIndicesException(
						"Indexer of index \"" + existing.name() + "\" created an index named \""
						+ replacement.name() + "\"; a rebuild must keep the name the index is registered under.",
						this
					);
				}

				violation = this.buildReplacementIndexData(replacement, wasUnique);
			}
			catch(final Throwable t)
			{
				// the replacement never becomes visible, so nothing else would ever release it
				releaseAbandonedIndexData(replacement, t);

				throw t;
			}

			// commit: nothing below can fail, so the group is never left between the two indices.
			this.swapIndex(existing, replacement, wasUnique, wasIdentity);

			if(violation != null)
			{
				throw violation.toException();
			}
		}

		/**
		 * Fills a replacement index from all entities and, if it backs a unique constraint, collects the
		 * first key collision instead of aborting on it - the rebuild has to complete either way.
		 * <p>
		 * Checking with {@link BitmapIndex.Internal#internalContains(Object)} against the replacement is
		 * correct for the same reason it is in {@link #buildIndexDataAndValidateUniqueness(EqHashTable)}:
		 * the replacement starts empty, so only entities added in this very pass can be found and every hit
		 * is therefore a genuinely different entity, with no own stale entry to exclude. Checking against
		 * the registered {@link #uniqueConstraints} instead would be wrong here - during a rebuild those
		 * still hold their full, not yet replaced data, in which every entity trivially collides with
		 * itself.
		 * <p>
		 * The entity is added <b>whether or not</b> it collided, so the finished index describes the
		 * entities as they actually are.
		 *
		 * @param replacement the not yet registered index to fill
		 * @param unique whether it backs a unique constraint and its keys must therefore be checked
		 * @return the collected violation, or {@code null} if the keys are unique (or unchecked)
		 */
		private UniquenessViolation<E> buildReplacementIndexData(
			final BitmapIndex.Internal<E, ?> replacement,
			final boolean                    unique
		)
		{
			if(!unique)
			{
				// no constraint to validate: plain rebuild, without paying for the per-entity containment checks.
				this.parent.iterateIndexed(replacement::internalAdd);

				return null;
			}

			final UniquenessViolation<E> violation = new UniquenessViolation<>();
			this.parent.iterateIndexed((final long entityId, final E entity) ->
			{
				collectUniquenessViolation(replacement, entityId, entity, violation);
				replacement.internalAdd(entityId, entity);
			});

			return violation.violatedIndex != null
				? violation
				: null
			;
		}

		/**
		 * Records a key collision of the entity about to be indexed in the given collector. Only the first
		 * collision is reported, so once one has been found the remaining entities are only counted - to
		 * report how many are affected - rather than replacing it.
		 *
		 * @param index the index being rebuilt
		 * @param entityId the entity's id
		 * @param entity the entity about to be indexed
		 * @param violation the collector of the violation to report after the rebuild
		 */
		private static <E> void collectUniquenessViolation(
			final BitmapIndex.Internal<E, ?> index    ,
			final long                       entityId ,
			final E                          entity   ,
			final UniquenessViolation<E>     violation
		)
		{
			if(!index.internalContains(entity))
			{
				return;
			}

			if(violation.violatedIndex != null)
			{
				violation.duplicateCount++;

				return;
			}

			violation.violatedIndex   = index   ;
			violation.entityId        = entityId;
			violation.violatingEntity = entity  ;
			violation.duplicateCount  = 1       ;
		}

		/**
		 * Replaces a registered index with an already fully built one, carrying its unique-constraint and
		 * identity-index membership over. Shared by {@link #update(Indexer)} and by a rebuild, which differ
		 * only in where the replacement's data comes from.
		 * <p>
		 * <b>Cannot fail</b>, which is what both callers rely on: the name the replacement is registered
		 * under is the one just freed, so registration cannot be rejected, and neither caller could undo a
		 * partial swap - the dropped index' data has been released by then. Everything that could reject the
		 * replacement is therefore checked before this is called.
		 * <p>
		 * Deliberately leaves {@link #rebuildCache()} to the caller: a rebuild swaps many indices and pays
		 * for it once at the end.
		 *
		 * @param existing the currently registered index
		 * @param replacement the fully built index to register in its place
		 * @param wasUnique whether {@code existing} backed a unique constraint
		 * @param wasIdentity whether {@code existing} was an identity index
		 */
		private void swapIndex(
			final BitmapIndex.Internal<E, ?> existing   ,
			final BitmapIndex.Internal<E, ?> replacement,
			final boolean                    wasUnique  ,
			final boolean                    wasIdentity
		)
		{
			// drops the old index' logic and data, releasing its off-heap memory. Identity removal is allowed
			// here and restored below; the cache rebuild is the caller's.
			this.internalRemoveIndex(existing.name(), true, false);
			if(wasUnique)
			{
				this.internalAddUniqueConstraint(replacement);
			}
			this.internalAddBitmapIndex(replacement);

			if(wasIdentity)
			{
				this.internalReplaceIdentityIndex(existing, replacement);
			}
		}

		/**
		 * Collects a failure across a best-effort loop: the first one is the one that will be rethrown, any
		 * further one is attached to it. Mirrors {@link #internalRemove(long, Object)}.
		 *
		 * @param first the failure collected so far, or {@code null}
		 * @param next the failure just encountered
		 * @return the failure to rethrow at the end
		 */
		private static Throwable addAsFailure(final Throwable first, final Throwable next)
		{
			if(first == null)
			{
				return next;
			}
			first.addSuppressed(next);

			return first;
		}

		/**
		 * Mutable collector for the unique-constraint violation encountered during a rebuild. A rebuild
		 * reports its violation only after it completed, and the per-entity check runs inside a lambda that
		 * cannot assign to local variables, hence this holder instead of plain locals.
		 *
		 * @param <E> the entity type
		 */
		private static final class UniquenessViolation<E>
		{
			BitmapIndex.Internal<E, ?> violatedIndex   = null;
			long                       entityId        = -1  ;
			E                          violatingEntity = null;
			long                       duplicateCount  = 0   ;

			UniquenessViolation()
			{
				super();
			}

			UniqueConstraintViolationExceptionBitmap toException()
			{
				return new UniqueConstraintViolationExceptionBitmap(
					this.entityId       ,
					null                ,
					this.violatingEntity,
					this.violatedIndex  ,
					"GigaMap.reindex() rebuilt the unique index \"" + this.violatedIndex.name()
					+ "\" from the current entity state, which holds duplicate keys (entities re-indexed"
					+ " under a key another entity already holds: " + this.duplicateCount
					+ "; the first one is reported here). The rebuild was completed, so the indices now"
					+ " describe the entities as they actually are: re-distinguish the colliding keys via"
					+ " update()/apply(), then call reindex() again."
				);
			}
		}

		@Override
		public <K> BitmapIndex<E, K> add(final Indexer<? super E, K> indexer)
		{
			final String indexName = validateIndexerIdentity(indexer);

			synchronized(this.parentMap())
			{
				this.ensureMutable("add index \"" + indexName + "\"");

				return this.internalAddIndex(indexer, true);
			}
		}

		@Override
		public <K> BitmapIndex<E, K> addWithoutInitialization(final Indexer<? super E, K> indexer)
		{
			final String indexName = validateIndexerIdentity(indexer);

			synchronized(this.parentMap())
			{
				this.ensureMutable("add index \"" + indexName + "\"");

				return this.internalAddIndex(indexer, false);
			}
		}

		/**
		 * Registers a single index without checking mutability. Callers must have passed
		 * {@link #ensureMutable(String)} and must still hold the parent-map monitor.
		 * <p>
		 * Registration is always the last step: where a back-fill happens at all ({@code initialize == true}),
		 * it runs while the index is still standalone, so an {@link Indexer} throwing for one of the
		 * already-present entities leaves this group untouched instead of a partially filled index registered
		 * (see {@link #buildIndexData(BitmapIndex.Internal)}).
		 *
		 * @param initialize whether to back-fill the new index from the already-present entities
		 *        (see {@link #addWithoutInitialization(Indexer)})
		 */
		private <K> BitmapIndex<E, K> internalAddIndex(final Indexer<? super E, K> indexer, final boolean initialize)
		{
			this.validateIndexToAdd(indexer);

			final BitmapIndex.Internal<E, K> index = indexer.createFor(this);
			try
			{
				// before anything is built, so that registration below cannot fail
				this.validateIndexToRegister(index);
				if(initialize)
				{
					this.buildIndexData(index);
				}
				this.internalAddBitmapIndex(index);
			}
			catch(final Throwable t)
			{
				releaseAbandonedIndexData(index, t);

				throw t;
			}
			this.rebuildCache();

			return index;
		}

		/**
		 * Validates an index that was just created and is about to be filled and registered.
		 * <p>
		 * {@link #validateIndexToAdd(Indexer)} only saw the <i>indexer's</i> name, but
		 * {@link Indexer#createFor(BitmapIndices)} may be overridden to name the index it creates
		 * differently. Checking here - while nothing has been built, dropped or registered yet - is what lets
		 * {@link #internalAddBitmapIndex(BitmapIndex.Internal)} be a step that cannot fail, which in turn is
		 * what makes a batch registration all-or-nothing and a redefinition atomic.
		 *
		 * @param index the freshly created, not yet registered index
		 */
		private void validateIndexToRegister(final BitmapIndex.Internal<E, ?> index)
		{
			this.validateIndexParent(index);

			final String indexName = index.name();
			if(indexName == null)
			{
				throw new IllegalArgumentException("Index name may not be null.");
			}
			if(this.bitmapIndices.get(indexName) != null)
			{
				throw new BitmapIndicesException(
					BitmapIndex.class.getSimpleName() + " already registered for name \"" + indexName + "\".",
					this
				);
			}
		}

		/**
		 * Validates that a freshly created index belongs to this group. Separate from
		 * {@link #validateIndexToRegister(BitmapIndex.Internal)} because a redefinition needs exactly this
		 * check and not the name check: the name it will be registered under is still occupied by the index
		 * it replaces.
		 *
		 * @param index the freshly created index
		 */
		private void validateIndexParent(final BitmapIndex.Internal<E, ?> index)
		{
			if(index.parent() != this)
			{
				throw new BitmapIndicesException(
					"Inconsistent parent reference for index " + BitmapIndex.class.getSimpleName()
					+ " \"" + index.name() + "\".",
					this
				);
			}
		}

		/**
		 * Validates a batch of freshly created indices, additionally rejecting a name collision <i>within</i>
		 * the batch - which the indexer-level validation cannot see either, for the reason given in
		 * {@link #validateIndexToRegister(BitmapIndex.Internal)}.
		 *
		 * @param indices the freshly created, not yet registered indices
		 */
		private void validateIndicesToRegister(final XGettingCollection<? extends BitmapIndex.Internal<E, ?>> indices)
		{
			final EqHashTable<String, BitmapIndex.Internal<E, ?>> byName = EqHashTable.New();
			for(final BitmapIndex.Internal<E, ?> index : indices)
			{
				this.validateIndexToRegister(index);
				if(!byName.add(index.name(), index))
				{
					throw new BitmapIndicesException(
						"Conflicted index name: \"" + index.name() + "\".",
						this
					);
				}
			}
		}

		/**
		 * Guards structural mutations against a parent {@link GigaMap} that is currently not mutable, applying
		 * the same classification an entity write applies (see {@link GigaMap.Internal#internalEnsureMutability()}):
		 * an explicit read-only mark, an in-progress iteration and a self-held reader fail fast, while readers
		 * open on other threads are waited out.
		 * <p>
		 * Must be called while holding the parent-map monitor. <b>The call may release that monitor while
		 * waiting</b>, so state read before it must be re-checked afterwards.
		 *
		 * @param operation description of the attempted change, used to build the error message
		 */
		private void ensureMutable(final String operation)
		{
			try
			{
				this.parentMap().internalEnsureMutability();
			}
			catch(final IllegalStateException e)
			{
				throw new BitmapIndicesException(
					"Cannot " + operation + ": the parent GigaMap is not mutable.",
					e,
					this
				);
			}
		}

		@Override
		public boolean removeIndex(final String name)
		{
			synchronized(this.parentMap())
			{
				this.ensureMutable("remove index \"" + name + "\"");

				return this.internalRemoveIndex(name, false, true) != null;
			}
		}

		/**
		 * @param allowIdentity whether an index that is currently an identity index may be removed.
		 *        {@code false} for the public {@link #removeIndex(String)}; {@code true} for
		 *        {@link #update(Indexer)}, which re-points the identity set to the rebuilt index.
		 * @param rebuildCache whether to rebuild the index cache here. {@code false} lets a caller that
		 *        performs further structural changes (e.g. {@link #update(Indexer)}) do a single rebuild
		 *        at the end instead of rebuilding twice.
		 * @return the removed index, or {@code null} if no index was registered under the given name
		 */
		private BitmapIndex.Internal<E, ?> internalRemoveIndex(final String name, final boolean allowIdentity, final boolean rebuildCache)
		{
			final BitmapIndex.Internal<E, ?> index = this.bitmapIndices.get(name);
			if(index == null)
			{
				return null;
			}
			if(!allowIdentity && this.isIdentityIndex(index))
			{
				throw new BitmapIndicesException(
					"Index \"" + name + "\" is registered as an identity index and cannot be removed; "
					+ "update the identity indices first.",
					this
				);
			}
			this.bitmapIndices.removeFor(name);
			this.internalRemoveUniqueConstraint(index);
			// release the dropped index' off-heap (Unsafe) memory immediately instead of waiting for the
			// segments' cleaners to run on GC (see BitmapLevel2#release).
			index.internalReleaseOffHeap();
			if(rebuildCache)
			{
				this.rebuildCache();
			}
			this.markStateChangeInstance();
			this.parent.internalReportIndexGroupStateChange(this);
			return index;
		}

		@Override
		public boolean removeUniqueConstraint(final String name)
		{
			synchronized(this.parentMap())
			{
				this.ensureMutable("remove unique constraint \"" + name + "\"");

				final BitmapIndex.Internal<E, ?> index = this.bitmapIndices.get(name);
				if(index == null || this.uniqueConstraints == null || !this.uniqueConstraints.contains(index))
				{
					return false;
				}
				this.internalRemoveUniqueConstraint(index);
				// the index stays registered, but its unique flag in the cache must be cleared.
				this.rebuildCache();
				this.markStateChangeInstance();
				return true;
			}
		}

		private void internalRemoveUniqueConstraint(final BitmapIndex.Internal<E, ?> index)
		{
			if(this.uniqueConstraints == null || !this.uniqueConstraints.contains(index))
			{
				return;
			}
			final BulkList<BitmapIndex.Internal<E, ?>> remaining = BulkList.New();
			for(final BitmapIndex.Internal<E, ?> e : this.uniqueConstraints)
			{
				if(e != index)
				{
					remaining.add(e);
				}
			}
			this.uniqueConstraints = remaining.isEmpty() ? null : ConstHashEnum.New(remaining);
			this.parent.internalReportIndexGroupStateChange(this);
		}

		private boolean isIdentityIndex(final BitmapIndex.Internal<E, ?> index)
		{
			if(this.identityIndices == null)
			{
				return false;
			}
			for(final BitmapIndex<E, ?> identityIndex : this.identityIndices)
			{
				if(identityIndex == index)
				{
					return true;
				}
			}
			return false;
		}

		@Override
		public <K> BitmapIndex<E, K> update(final Indexer<? super E, K> indexer)
		{
			final String name = validateIndexerIdentity(indexer);

			synchronized(this.parentMap())
			{
				this.ensureMutable("update indexer \"" + name + "\"");

				final BitmapIndex.Internal<E, ?> existing = this.bitmapIndices.get(name);
				if(existing == null)
				{
					throw new BitmapIndicesException(
						"No index registered for name \"" + name + "\" to update; use add(...) to create it.",
						this
					);
				}
				final boolean wasUnique   = this.isUniqueConstraint(existing);
				final boolean wasIdentity = this.isIdentityIndex(existing);

				// Build the new index' data against all existing entities BEFORE dropping the old one, so
				// that a failure under the new logic - a uniqueness violation or a throwing indexer - leaves
				// the existing index untouched (atomic failure). The new index is standalone (not yet
				// registered) for the whole build.
				final BitmapIndex.Internal<E, K> index = indexer.createFor(this);
				try
				{
					// Everything #internalAddBitmapIndex could reject, checked while nothing has been changed
					// yet - the registration below runs after the old index has been dropped, so it must not
					// be able to fail. The name check is the update-specific half: the replacement must carry
					// the very name that selected the index being replaced, whereas #createFor may be
					// overridden to name the index it creates differently.
					this.validateIndexParent(index);
					if(!name.equals(index.name()))
					{
						throw new BitmapIndicesException(
							"Indexer \"" + name + "\" created an index named \"" + index.name()
							+ "\"; a redefinition must keep the name that selects the index to replace.",
							this
						);
					}
					if(wasUnique)
					{
						if(!index.isSuitableAsUniqueConstraint())
						{
							throw new BitmapIndicesException(
								"Index not suited as a unique constraint: \"" + index.name() + "\" class " + index.getClass(),
								this
							);
						}
						final EqHashTable<String, BitmapIndex.Internal<E, ?>> indices = EqHashTable.New();
						indices.add(index.name(), index);
						this.buildIndexDataAndValidateUniqueness(indices);
					}
					else
					{
						this.buildIndexData(index);
					}
				}
				catch(final Throwable t)
				{
					releaseAbandonedIndexData(index, t);

					throw t;
				}

				// commit: nothing below can fail.
				this.swapIndex(existing, index, wasUnique, wasIdentity);
				this.rebuildCache();
				return index;
			}
		}

		private void internalReplaceIdentityIndex(final BitmapIndex<E, ?> oldIndex, final BitmapIndex<E, ?> newIndex)
		{
			if(this.identityIndices == null)
			{
				return;
			}
			final BulkList<BitmapIndex<E, ?>> resolved = BulkList.New();
			for(final BitmapIndex<E, ?> identityIndex : this.identityIndices)
			{
				resolved.add(identityIndex == oldIndex ? newIndex : identityIndex);
			}
			this.internalSetIdentityIndices(ConstHashEnum.New(resolved));
			this.markStateChangeInstance();
		}

		@Override
		public final BitmapIndices<E> addAll(final Iterable<? extends Indexer<? super E, ?>> indexers)
		{
			// The indexers are traversed twice below, so the passed Iterable must be materialized: a single-use
			// one (e.g. stream-backed) would come up empty on the second pass and silently register no index at
			// all. Materializing outside the monitor also keeps arbitrary caller code (a lazily evaluated
			// Iterable) from running while the parent map is locked.
			final XGettingCollection<? extends Indexer<? super E, ?>> requested = BulkList.New(indexers);

			synchronized(this.parentMap())
			{
				this.ensureMutable("add indices");

				// Validation before changing any state, including name conflicts within the batch itself:
				// #validateIndexToAdd only sees the already registered names, so a duplicate inside the batch
				// would pass validation and then be dropped silently while registering.
				final EqHashTable<String, Indexer<? super E, ?>> byName = EqHashTable.New();
				for(final Indexer<? super E, ?> indexer : requested)
				{
					this.validateIndexToAdd(indexer);
					if(!byName.add(indexer.name(), indexer))
					{
						throw new BitmapIndicesException(
							"Conflicted index name: \"" + indexer.name() + "\".",
							this
						);
					}
				}

				// Build every index' data while they are all still standalone, so that an indexer throwing
				// for one of the already-present entities registers none of the batch instead of leaving it
				// half applied - with the throwing index registered and partially filled at that.
				// Deliberately a plain list, not a table keyed by name: the validation above de-duplicates
				// the indexers' names, but #createFor may be overridden to name the index differently, and
				// a keyed collection would silently drop such a collision - both from the batch and from
				// the release below.
				final BulkList<BitmapIndex.Internal<E, ?>> indices = BulkList.New(requested.intSize());
				try
				{
					for(final Indexer<? super E, ?> indexer : requested)
					{
						indices.add(indexer.createFor(this));
					}
					// Names the indexers did not reveal are checked before anything is built, so that the
					// registration loop below cannot fail with part of the batch already registered.
					this.validateIndicesToRegister(indices);
					this.buildIndexData(indices);
				}
				catch(final Throwable t)
				{
					releaseAbandonedIndexData(indices, t);

					throw t;
				}

				for(final BitmapIndex.Internal<E, ?> index : indices)
				{
					this.internalAddBitmapIndex(index);
				}
				this.rebuildCache();
			}

			return this;
		}
		
		@Override
		public <K> BitmapIndex<E, K> ensure(final Indexer<? super E, K> indexer)
		{
			return this.ensureBitmapIndex(indexer);
		}
		
		/**
		 * Ensures that all indices are optimized for small memory usage ("consolidated").
		 */
		@Override
		public final void ensureOptimizedSize()
		{
			synchronized(this.parentMap())
			{
				this.iterate(BitmapIndex::ensureOptimizedSize);
			}
		}
		
		/**
		 * Ensures that all indices are optimized for adding new entries as fast as possible.
		 */
		@Override
		public final void ensureOptimizedPerformance()
		{
			synchronized(this.parentMap())
			{
				this.iterate(BitmapIndex::ensureOptimizedPerformance);
			}
		}
		
		@Override
		public void internalPrepareIndicesUpdate(final E replacedEntity)
		{
			if(this.bitmapIndices.isEmpty())
			{
				// no-op
				return;
			}

			this.cachedStateChangeApplied = false;

			// Derive state handlers for the state of the replaced entity.
			this.createChangeHandlers(this.cachedPrevChangeHandlers, replacedEntity);
		}
		
		@Override
		public void internalFinishIndicesUpdate()
		{
			if(this.bitmapIndices.isEmpty())
			{
				// no-op
				return;
			}

			this.clearCachedChangeHandlers();
		}
		
		@Override
		public final void internalRemovePreparedState(final long entityId)
		{
			if(this.bitmapIndices.isEmpty())
			{
				// no-op
				return;
			}

			if(this.cachedStateChangeApplied)
			{
				/*
				 * The entries were already moved to the entity's new state, which the removal re-derives
				 * from that same state and can therefore locate itself. The prepared previous state no
				 * longer describes anything that is in the indices.
				 */
				return;
			}

			/*
			 * The cached prev handlers de-index the entity's previous state without re-running any user
			 * code, which is exactly what a removal that re-derives the keys from the entity's mutated
			 * state cannot do.
			 */
			try
			{
				for(final ChangeHandler cachedPrevChangeHandler : this.cachedPrevChangeHandlers)
				{
					cachedPrevChangeHandler.removeFromIndex(entityId);
				}
			}
			finally
			{
				this.markStateChangeChildren();
			}
		}

		@Override
		public final void internalUpdateIndices(
			final long                         entityId         ,
			final E                            replacedEntity   ,
			final E                            entity           ,
			final CustomConstraints<? super E> customConstraints
		)
		{
			if(this.bitmapIndices.isEmpty())
			{
				// no-op
				return;
			}

			/*
			 * Phase 1: derive the new change handlers and run all checks. This is where all user code
			 * (indexers, key equality, constraints) runs; no index state has been mutated, yet, since
			 * handler derivation defers entry creation to changeInIndex (see NewKeyChangeChandler).
			 * A throw here therefore needs no cleanup in this group: whether the entity's previous state
			 * must be de-indexed depends on whether the calling context keeps or removes the entity,
			 * which only that context can decide (see IndexGroup.Internal#internalRemovePreparedState).
			 */
			// Derive state handlers for the new, potentially changed state
			this.createChangeHandlers(this.cachedNewChangeHandlers, entity);

			if(customConstraints != null)
			{
				customConstraints.check(entityId, replacedEntity, entity);
			}

			// Evaluate changes for each index
			for(int i = 0; i < this.cachedPrevChangeHandlers.length; i++)
			{
				if(this.cachedPrevChangeHandlers[i].isEqual(this.cachedNewChangeHandlers[i]))
				{
					// Mark index position to be irrelevant (unchanged)
					this.cachedNewChangeHandlers[i] = null;
					continue;
				}
				if(this.cachedIsUniqueIndex[i])
				{
					// A key held only by the entity being updated (e.g. its own stale entry after a
					// class evolution) is not a duplicate; only a different entity is a violation.
					if(this.cachedIndices[i].internalContains(entity, entityId))
					{
						throw new UniqueConstraintViolationExceptionBitmap(entityId, replacedEntity, entity, this.cachedIndices[i]);
					}
				}
			}

			// Phase 2: update indices for all actual changes. Runs no more user code apart from key hashing.
			try
			{
				for(int i = 0; i < this.cachedNewChangeHandlers.length; i++)
				{
					// Skip index positions for unchanged values
					if(this.cachedNewChangeHandlers[i] == null)
					{
						continue;
					}
					this.cachedNewChangeHandlers[i].changeInIndex(entityId, this.cachedPrevChangeHandlers[i]);

					/*
					 * Set only after a change went through: changeInIndex de-indexes the previous key
					 * first, so a throw from the very first one (notably the stale-index path) leaves
					 * this group's entries untouched - and the prepared previous state then still
					 * describes them exactly, which is what makes the cleanup valid.
					 */
					this.cachedStateChangeApplied = true;
				}
			}
			finally
			{
				this.markStateChangeChildren();
			}
		}
		
		private boolean isUniqueConstraint(final BitmapIndex.Internal<E, ?> index)
		{
			return index.isSuitableAsUniqueConstraint()
				&& this.uniqueConstraints != null
				&& this.uniqueConstraints.contains(index)
			;
		}

		@Override
		public final BitmapIndex<E, String> get(final String name)
		{
			synchronized(this.parentMap())
			{
				return this.internalGet(String.class, name);
			}
		}

		@Override
		public final <K> BitmapIndex<E, K> get(final Class<K> keyType, final String indexName)
		{
			synchronized(this.parentMap())
			{
				return this.internalGet(keyType, indexName);
			}
		}

		@Override
		public final <K> BitmapIndex.Internal<E, K> internalGet(final Class<K> keyType, final String indexName)
		{
			final BitmapIndex.Internal<E, ?> index = this.bitmapIndices.get(indexName);
			if(index == null)
			{
				// no index at all registered for that name, regardless of keyType.
				return null;
			}
			
			// might throw an exception here instead of "faking" a lookup miss.
			@SuppressWarnings("unchecked") // cast safety guaranteed by adding logic
			final BitmapIndex.Internal<E, K> bitmapIndex = index.keyType() == keyType
				? (BitmapIndex.Internal<E, K>)index
				: null
			;
			
			return bitmapIndex;
		}
		
		@Override
		public <K, I extends Indexer<E, K>> I getIndexer(final Class<I> indexerType, final String name)
		{
			synchronized(this.parentMap())
			{
				final BitmapIndex.Internal<E, ?> index = this.bitmapIndices.get(name);
				if(index != null)
				{
					return indexerType.cast(index.indexer());
				}
				
				return null;
			}
		}
		
		final void internalSetIdentityIndices(final XImmutableEnum<? extends BitmapIndex<E, ?>> identityIndices)
		{
			this.identityIndices = identityIndices;
		}
		
		final void internalSetUniqueConstraints(final XImmutableEnum<BitmapIndex.Internal<E, ?>> uniqueConstraints)
		{
			this.uniqueConstraints = uniqueConstraints;
		}
		
		@Override
		public final XImmutableEnum<? extends BitmapIndex<E, ?>> identityIndices()
		{
			return this.identityIndices;
		}

		@Override
		public final XImmutableEnum<? extends BitmapIndex<E, ?>> uniqueConstraints()
		{
			return this.uniqueConstraints;
		}
		
		@Override
		public final BitmapIndices<E> setIdentityIndices(final XGettingEnum<? extends IndexIdentifier<? super E, ?>> identityIndices)
		{
			if(X.hasNoContent(identityIndices))
			{
				return this;
			}
			
			final BulkList<BitmapIndex<E, ?>> resolved = BulkList.New(identityIndices.size());

			synchronized(this.parentMap())
			{
				this.ensureMutable("set identity indices");
				for(final IndexIdentifier<? super E, ?> i : identityIndices)
				{
					final BitmapIndex<E, ?> index = i.resolveFor(this);
					resolved.add(index);
				}
				this.internalSetIdentityIndices(ConstHashEnum.New(resolved));
				this.markStateChangeInstance();
			}
			
			return this;
		}
		
		/**
		 * Registers an index whose data is already complete. Deliberately performs no back-fill: filling
		 * happens beforehand, while the index is still standalone (see {@link #buildIndexData(BitmapIndex.Internal)}).
		 * <p>
		 * This step must not fail - callers register a batch index by index, and a redefinition registers the
		 * replacement after having dropped the original - so everything that could reject the index is checked
		 * by {@link #validateIndexToRegister(BitmapIndex.Internal)} before anything is built or dropped. The
		 * guard below therefore only asserts that invariant rather than enforcing it; a caller reaching it has
		 * skipped the validation.
		 */
		private void internalAddBitmapIndex(final BitmapIndex.Internal<E, ?> index)
		{
			// #add does not overwrite, so a taken name would otherwise leave the caller believing an index it
			// never registered is in place.
			if(index.parent() != this || !this.bitmapIndices.add(index.name(), index))
			{
				throw new BitmapIndicesException(
					"Index " + BitmapIndex.class.getSimpleName() + " \"" + index.name()
					+ "\" cannot be registered: inconsistent parent reference, or the name is already taken.",
					this
				);
			}

			this.markStateChangeInstance();
			this.parent.internalReportIndexGroupStateChange(this);
		}
		

		
		static <E> EqHashTable<String, BitmapIndex.Internal<E, ?>> createHashTable(final Class<?> keyType)
		{
			return EqHashTable.New();
		}
		
		final <I> BitmapIndex<E, I> ensureBitmapIndex(final Indexer<? super E, I> indexer)
		{
			final String indexName = validateIndexerIdentity(indexer);

			synchronized(this.parentMap())
			{
				BitmapIndex<E, I> index = this.get(indexer.keyType(), indexName);
				if(index != null)
				{
					// Already present: no structural change, so the mutability guard is deliberately not
					// reached. This keeps ensure() a no-op on a read-only map, letting a read-only replica run
					// the identical startup schema declaration.
					return index;
				}

				this.ensureMutable("add index \"" + indexName + "\"");

				// The guard may have released the parent-map monitor while waiting for foreign readers, so
				// another thread may have registered the index meanwhile. Re-check instead of letting
				// #validateIndexToAdd turn an idempotent ensure() into a "name already taken" failure.
				index = this.get(indexer.keyType(), indexName);

				return index != null
					? index
					: this.internalAddIndex(indexer, true)
				;
			}
		}
		
		@Override
		protected final void clearChildrenStateChangeMarkers()
		{
			this.bitmapIndices.values().iterate(BitmapIndex.Internal::clearStateChangeMarkers);
		}
		
		@Override
		protected final void storeChildren(final Storer storer)
		{
			// this is just a local, partial lock that does NOT protect the whole giga map storing process. See GigaMap#store.
			synchronized(this.parentMap())
			{
				super.storeChildren(storer);
			}
		}

		@Override
		protected final void storeChangedChildren(final Storer storer)
		{
			for(final BitmapIndex.Internal<E, ?> index : this.bitmapIndices.values())
			{
				// must take a detour over the TypeHandler here as well because of interface abstraction.
				storer.store(index);
			}
		}
		
		private void validateIndexToAdd(final Indexer<? super E, ?> indexer)
		{
			final String indexName = validateIndexerIdentity(indexer);

			// Index name may not be taken, yet.
			final BitmapIndex<E, ?> index = this.bitmapIndices.get(indexName);
			if(index != null)
			{
				throw new RuntimeException(BitmapIndex.class.getSimpleName() + " already registered for name \"" + index.name() + "\".");
			}
		}

		/**
		 * Validates that the indexer and its registry key exist at all, independent of whether that key is
		 * still free. Called by every entry point before the indexer is dereferenced - both to build the
		 * mutability guard's message and to look the index up - so that a {@code null} indexer or name yields
		 * the same {@link IllegalArgumentException} everywhere instead of a {@link NullPointerException} from
		 * whichever dereference happens to come first.
		 *
		 * @param indexer the indexer to validate
		 * @return the indexer's non-null name
		 */
		private static String validateIndexerIdentity(final Indexer<?, ?> indexer)
		{
			if(indexer == null)
			{
				throw new IllegalArgumentException("Indexer may not be null.");
			}

			final String indexName = indexer.name();
			if(indexName == null)
			{
				throw new IllegalArgumentException("Index name may not be null.");
			}

			return indexName;
		}
		
		private void internalAddUniqueConstraint(final BitmapIndex.Internal<E, ?> index)
		{
			if(this.uniqueConstraints == null)
			{
				this.uniqueConstraints = ConstHashEnum.New(index);
			}
			else if(this.uniqueConstraints.contains(index))
			{
				return;
			}
			else
			{
				// no need for a set as the #contains call above already ensured uniqueness after the #add.
				final BulkList<BitmapIndex.Internal<E, ?>> mutable = BulkList.New(this.uniqueConstraints);
				mutable.add(index);
				this.uniqueConstraints = ConstHashEnum.New(mutable);
			}
			
			// report change in case #1 and #3 (#2 aborts)
			this.parent.internalReportIndexGroupStateChange(this);
		}
		
		@Deprecated
		@Override
		public final UniqueConstraints<E> addUniqueConstraint(final String indexName, final Indexer<? super E, ?> indexer)
		{
			// note: indexName is ignored; the constraint is registered under the indexer's own name.
			// validation and registering creates so many instances that this one detour instance does not matter.
			this.addUniqueConstraints(X.Constant(indexer));

			return this;
		}
		
		@Override
		public final UniqueConstraints<E> addUniqueConstraints(final Iterable<? extends Indexer<? super E, ?>> indexers)
		{
			// #internalAddUniqueConstraints traverses the indexers twice, so the passed Iterable must be
			// materialized: a single-use one (e.g. stream-backed) would come up empty on the second pass and
			// silently register neither index nor unique constraint. Materializing outside the monitor also
			// keeps arbitrary caller code (a lazily evaluated Iterable) from running while the map is locked.
			final XGettingCollection<? extends Indexer<? super E, ?>> requested = BulkList.New(indexers);

			synchronized(this.parentMap())
			{
				this.ensureMutable("add unique constraints");

				this.internalAddUniqueConstraints(requested);
			}

			return this;
		}

		/**
		 * Registers the given indexers as unique constraints without checking mutability. Callers must have
		 * passed {@link #ensureMutable(String)} and must still hold the parent-map monitor.
		 * <p>
		 * Takes a re-traversable collection on purpose: the indexers are traversed twice.
		 */
		private void internalAddUniqueConstraints(final XGettingCollection<? extends Indexer<? super E, ?>> indexers)
		{
			// Basic validation before changing any state.
			for(final Indexer<? super E, ?> indexer : indexers)
			{
				this.validateIndexToAdd(indexer);
			}

			// Building unique indices, their data and data-related validation.
			// Every created index is collected separately from the by-name table: one that fails validation
			// is not in that table (a suitability failure never reaches it, a name collision is what the
			// insert rejected), and the cleanup below must not depend on which of the two it landed in.
			final EqHashTable<String, BitmapIndex.Internal<E, ?>> indices = EqHashTable.New();
			final BulkList<BitmapIndex.Internal<E, ?>>            created = BulkList.New();
			try
			{
				this.buildUniqueIndices(indexers, indices, created);
				// names the indexers did not reveal, checked before anything is built or registered
				this.validateIndicesToRegister(indices.values());
				this.buildIndexDataAndValidateUniqueness(indices);
			}
			catch(final Throwable t)
			{
				releaseAbandonedIndexData(created, t);

				throw t;
			}

			// When everything is guaranteed to be valid and consistent, the indices - whose data is complete
			// by now - are actually registered.
			for(final BitmapIndex.Internal<E, ?> index : indices.values())
			{
				this.internalAddUniqueConstraint(index);
				this.internalAddBitmapIndex(index);
			}
			this.rebuildCache();
		}

		@Override
		public final UniqueConstraints<E> ensureUniqueConstraint(final Indexer<? super E, ?> indexer)
		{
			final String indexName = validateIndexerIdentity(indexer);

			synchronized(this.parentMap())
			{
				if(this.isRegisteredUniqueConstraint(indexName))
				{
					// Already registered as a unique constraint -> idempotent no-op. No structural change, so
					// the mutability guard is deliberately not reached (see #ensureBitmapIndex).
					return this;
				}

				this.ensureMutable("add unique constraint \"" + indexName + "\"");

				// The guard may have released the parent-map monitor while waiting for foreign readers, so
				// re-check before creating the constraint (see #ensureBitmapIndex).
				if(this.isRegisteredUniqueConstraint(indexName))
				{
					return this;
				}

				// create + validate against existing data (throws if the name is taken by a non-unique index)
				this.internalAddUniqueConstraints(X.Constant(indexer));

				return this;
			}
		}

		private boolean isRegisteredUniqueConstraint(final String indexName)
		{
			// registry key is the indexer's name (a unique constraint is registered under index.name())
			final BitmapIndex.Internal<E, ?> existing = this.bitmapIndices.get(indexName);

			return existing != null && this.uniqueConstraints != null && this.uniqueConstraints.contains(existing);
		}

		/**
		 * @param indices receives the created indices by name, for the subsequent build and registration
		 * @param created receives every created index, including one this method then rejects, so that the
		 *        caller can discard all of them regardless of where the failure happened
		 */
		private void buildUniqueIndices(
			final XGettingCollection<? extends Indexer<? super E, ?>> indexers,
			final EqHashTable<String, BitmapIndex.Internal<E, ?>>     indices ,
			final BulkList<BitmapIndex.Internal<E, ?>>                created
		)
		{
			for(final Indexer<? super E, ?> indexer : indexers)
			{
				final BitmapIndex.Internal<E, ?> index = indexer.createFor(this);
				created.add(index);
				if(!index.isSuitableAsUniqueConstraint())
				{
					throw new BitmapIndicesException(
						"Index not suited as a unique constraint: \"" + index.name() + "\" class " + index.getClass(),
						this
					);
				}
				if(!indices.add(index.name(), index))
				{
					throw new BitmapIndicesException(
						"Conflicted index name: \"" + index.name() + "\".",
						this
					);
				}
			}
		}
		
		/**
		 * Back-fills a standalone - created, but not yet registered - index from the entities the parent map
		 * already holds.
		 * <p>
		 * Must run <b>before</b> {@link #internalAddBitmapIndex(BitmapIndex.Internal)}: the back-fill is the
		 * only step that runs user code ({@link Indexer#index(Object)}), so an indexer throwing for one of
		 * the already-present entities would otherwise leave a partially filled index registered - silently
		 * answering queries with a torso of the data, and persisted by the next {@code store()}.
		 * <p>
		 * Releasing the off-heap memory of an index abandoned because this failed is the caller's job: the
		 * caller owns the index for its whole standalone life - creation, validation, filling, registration -
		 * and a failure anywhere in that span must discard it (see {@link #releaseAbandonedIndexData}). An
		 * index that never gets registered is never reached by
		 * {@link #internalRemoveIndex(String, boolean, boolean)}'s release.
		 *
		 * @param index the standalone index to fill
		 */
		private void buildIndexData(final BitmapIndex.Internal<E, ?> index)
		{
			this.parent.iterateIndexed(index::internalAdd);
		}

		/**
		 * Back-fills several standalone indices in a single pass over the entities, which materializes every
		 * lazily loaded segment once instead of once per index. The indices are independent, so the per-entity
		 * order is the only difference to filling them one after another.
		 * <p>
		 * For the failure contract see {@link #buildIndexData(BitmapIndex.Internal)}: none of the indices is
		 * registered yet, so a throwing indexer leaves the group untouched - the batch is all-or-nothing.
		 *
		 * @param indices the standalone indices to fill
		 */
		private void buildIndexData(final XGettingCollection<? extends BitmapIndex.Internal<E, ?>> indices)
		{
			if(indices.isEmpty())
			{
				// nothing to fill: skip the pass over the entities, which would otherwise materialize every
				// lazily loaded segment to hand each one to no index at all.
				return;
			}

			this.parent.iterateIndexed((final long entityId, final E entity) ->
			{
				for(final BitmapIndex.Internal<E, ?> index : indices)
				{
					index.internalAdd(entityId, entity);
				}
			});
		}

		private void buildIndexDataAndValidateUniqueness(
			final EqHashTable<String, BitmapIndex.Internal<E, ?>> indices
		)
		{
			if(indices.isEmpty())
			{
				// see #buildIndexData(XGettingCollection)
				return;
			}

			this.parent.iterateIndexed((final long entityId, final E entity) ->
			{
				for(final BitmapIndex.Internal<E, ?> index : indices.values())
				{
					if(index.internalContains(entity))
					{
						throw new UniqueConstraintViolationExceptionBitmap(entityId, null, entity, index);
					}
					index.internalAdd(entityId, entity);
				}
			});
		}

		private static void releaseAbandonedIndexData(
			final XGettingCollection<? extends BitmapIndex.Internal<?, ?>> indices,
			final Throwable                                               cause
		)
		{
			for(final BitmapIndex.Internal<?, ?> index : indices)
			{
				releaseAbandonedIndexData(index, cause);
			}
		}

		/**
		 * Releases the off-heap memory of an index that is being abandoned because building its data failed,
		 * mirroring the eager release a dropped index gets (see {@link #internalRemoveIndex(String, boolean, boolean)}).
		 * A failure of the release itself is attached to the original cause rather than replacing it: what
		 * made the build fail is what the caller has to act on.
		 */
		private static void releaseAbandonedIndexData(final BitmapIndex.Internal<?, ?> index, final Throwable cause)
		{
			try
			{
				index.internalReleaseOffHeap();
			}
			catch(final Throwable t)
			{
				cause.addSuppressed(t);
			}
		}
		
		@Override
		public final void accessUniqueConstraints(final Consumer<? super XImmutableEnum<? extends GigaIndex<E>>> logic)
		{
			if(this.uniqueConstraints == null)
			{
				return;
			}
			logic.accept(this.uniqueConstraints);
		}

		@Override
		public final void accessUniqueIndices(final Consumer<? super XImmutableEnum<? extends BitmapIndex<E, ?>>> logic)
		{
			if(this.uniqueConstraints == null)
			{
				return;
			}
			logic.accept(this.uniqueConstraints);
		}
		
		@Override
		public final void accessIndices(final Consumer<? super XGettingTable<String, ? extends BitmapIndex<E, ?>>> logic)
		{
			synchronized(this.parentMap())
			{
				logic.accept(this.bitmapIndices);
			}
		}
		
		
		
		
		// no idea why "BitmapIndex.Internal" is not directly compatible with "? extends BitmapIndex", but here we are.
		protected static final class EntryIterator<E, I extends BitmapIndex<E, ?>>
		implements Iterator<KeyValue<String, ? extends BitmapIndex<E, ?>>>
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			private final Iterator<KeyValue<String, I>> iterator;
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////

			EntryIterator(final EqHashTable<String, I> bitmapIndices)
			{
				super();
				this.iterator = bitmapIndices.iterator();
			}
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////

			@Override
			public boolean hasNext()
			{
				return this.iterator.hasNext();
			}

			@Override
			public KeyValue<String, I> next()
			{
				return this.iterator.next();
			}
			
		}
		
		@Override
		public <I extends Consumer<? super BitmapIndex<E, ?>>> I iterate(final I iterator)
		{
			synchronized(this.parentMap())
			{
				for(final KeyValue<String, ? extends BitmapIndex<E, ?>> entry : this)
				{
					iterator.accept(entry.value());
				}
			}
			
			return iterator;
		}

		@Override
		public final Iterator<KeyValue<String, ? extends BitmapIndex<E, ?>>> iterator()
		{
			synchronized(this.parentMap())
			{
				return new EntryIterator<>(this.bitmapIndices.copy());
			}
		}

		@Override
		public Statistics<E> createStatistics()
		{
			// concurrency handled inside
			return DefaultStatistics.createStatistics(this);
		}
		
	}
	
	public interface Statistics<E>
	{
		public XGettingTable<String, BitmapIndex.Statistics<E>> entries();
		
		public default int entryCount()
		{
			return this.entries().intSize();
		}
		
		public int totalDataMemorySize();
		
		public default VarString assemble(final VarString vs)
		{
			return this.assemble(vs, Integer.MAX_VALUE);
		}
		
		public VarString assemble(VarString vs, int levels);
	}
	
}

