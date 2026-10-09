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

import static org.eclipse.serializer.util.X.notNull;

import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.collections.types.XGettingTable;
import org.eclipse.serializer.collections.types.XIterable;
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.store.gigamap.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Mutable vector index registry and manager.
 * <p>
 * This is the container that manages multiple {@link VectorIndex} instances.
 *
 * @param <E> the entity type
 */
public interface VectorIndices<E>
extends
IndexGroup.Internal<E>,
XIterable<VectorIndex<E>>,
Iterable<KeyValue<String, ? extends VectorIndex<E>>>
{
    /**
     * Adds a vector index to this group and back-fills it from the entities the parent map already holds.
     * <p>
     * <b>Behavior on failure:</b> the index is built before it is registered, so if {@code vectorizer}
     * throws for one of the already-present entities, nothing is registered and this group is left exactly
     * as it was: no index is available under the given name, no sibling index is affected, and nothing of the
     * attempt is persisted by a subsequent {@code store()}. The vectorizer's exception is the one propagated -
     * should discarding the half-built index fail in turn, that secondary failure is attached to it as a
     * suppressed exception. The name stays free, so the call can simply be repeated, for example after a
     * transient failure of an embedding service.
     * <p>
     * With {@link VectorIndexConfiguration#eventualIndexing() eventual indexing}, the vectors of all
     * present entities are computed and validated before registration, but the graph is built in the
     * background afterwards, as for any later add. A vectorizer failure for an entity's own vector is
     * therefore still reported here; failures during the background graph construction are not - in
     * embedded mode that includes the vectorizer being called there to score neighbours.
     *
     * @param name          the name of the index
     * @param configuration the index configuration (dimension, similarity function, etc.)
     * @param vectorizer    the logic to extract vectors from entities
     * @return the resulting index
     * @throws IllegalStateException if an index with the same name is already registered
     */
    public VectorIndex<E> add(
        String name,
        VectorIndexConfiguration configuration,
        Vectorizer<? super E> vectorizer
    );

    /**
     * Ensures that a vector index exists in this group.
     * <p>
     * <b>If an index of that name already exists, it is returned unchanged and the supplied
     * {@code configuration} and {@code vectorizer} are ignored</b> - the persisted ones win. This
     * call cannot be used to reconfigure an existing index: after the parent map has been loaded
     * from storage the index is already registered, so passing a changed configuration here has no
     * effect and no error is reported. Inspect the effective settings with
     * {@code get(name).configuration()}.
     * <p>
     * Changing the configuration of an existing index requires {@link #removeIndex(String)} followed
     * by {@link #add(String, VectorIndexConfiguration, Vectorizer)}, which re-indexes every entity in
     * the parent map.
     * <p>
     * <b>That holds for an on-disk index too, whatever the configuration changed to, and without
     * deleting anything by hand.</b> {@link #removeIndex(String)} leaves the index directory's
     * {@code .graph} and {@code .meta} files in place - it only closes the index and drops the
     * registry entry - but the index that
     * {@link #add(String, VectorIndexConfiguration, Vectorizer)} creates never adopts them. It is a
     * new instance, so its structural modification counter starts at zero, while any {@code .meta}
     * that was ever written carries at least one. The load is refused on that witness before the
     * recorded settings are even reached, and the graph is rebuilt from the vectors in the parent
     * map.
     * <p>
     * The recorded settings decide the other path, where an index comes back through storage with
     * its counter intact so the content witnesses agree. What is recorded is the format version, the
     * dimension, three content witnesses, and the settings that decide what the graph contains - the
     * vector storage mode, the approximate scoring mode, and the quantization counts those two
     * encode with. A divergence in any of them rejects the graph and rebuilds it rather than serving
     * it under a configuration it was not written for.
     * <p>
     * The two counts are compared in <i>effective</i> form, which makes them narrower than the modes
     * they belong to. An automatic count and the value it resolves to describe the same graph and so
     * compare equal, and a count that the recorded format does not encode with is not compared at
     * all - changing {@code pqSubspaces} while the scoring mode is
     * {@link ApproximateScoring#NONE}, or {@code nvqSubvectors} while storage is
     * {@link VectorStorage#INLINE}, changes nothing in the file and triggers no rebuild.
     * <p>
     * What is <b>not</b> recorded is everything that shapes the graph without changing what a reader
     * must know to interpret it: {@code maxDegree}, {@code beamWidth}, {@code alpha},
     * {@code neighborOverflow} and
     * {@link VectorIndexConfiguration#similarityFunction() similarityFunction}. Nothing in the file
     * would contradict a new value for any of those, so a graph built under a previous one would be
     * served under it if the two were ever brought together. The counter witness is what keeps that
     * from arising: presenting a graph with a different configuration means building a new index,
     * and a new index does not adopt an existing graph. The similarity function is the one where
     * that would matter rather than merely costing graph quality, since edges are built by searching
     * with it and reusing them under another metric would leave traversal following neighbours
     * chosen for the wrong distance.
     * <p>
     * <b>Behavior on failure:</b> as for {@link #add(String, VectorIndexConfiguration, Vectorizer)} - if
     * the index has to be created and its back-fill throws, nothing is registered, so a later
     * {@code ensure} with the same name creates the index anew instead of returning a partially filled one.
     *
     * @param name          the name of the index
     * @param configuration the index configuration, ignored if an index of that name already exists
     * @param vectorizer    the vectorizer, ignored if an index of that name already exists
     * @return the resulting index (existing or newly created)
     */
    public VectorIndex<E> ensure(
        String name,
        VectorIndexConfiguration configuration,
        Vectorizer<? super E> vectorizer
    );

    /**
     * Gets the registered index with given name, or {@code null}.
     *
     * @param name the name of the index to search
     * @return the found index or {@code null}
     */
    public VectorIndex<E> get(String name);

    /**
     * Removes the vector index registered under the given name, dropping it and its data.
     * <p>
     * The index is {@link VectorIndex#close() closed} first to release its background, search and
     * (if any) disk resources. Vector data held inside the object graph is reclaimed by the storage's
     * garbage collection on the next housekeeping cycle after the surrounding {@link GigaMap} is
     * stored; index artifacts kept in an external, application-managed directory are not deleted.
     *
     * @param name the name of the index to remove
     * @return {@code true} if an index with that name existed and was removed, {@code false} otherwise
     * @throws RuntimeException if the parent {@link GigaMap} is read-only
     */
    public boolean removeIndex(String name);

    /**
     * Changes where an existing on-disk index keeps its files, <b>effective from the next time the index
     * is loaded</b>. The running index keeps using the directory it was opened in.
     * <p>
     * The index's configuration is replaced by a copy that differs only in its location
     * ({@link VectorIndexConfiguration#withIndexLocation(IndexLocation)}); nothing else of the index
     * changes, no vector is touched, and no files are moved or deleted. The change reaches the storage
     * with the next store of the parent {@link GigaMap}; a restart before that keeps the old location.
     * <p>
     * Typical migration to a {@link IndexLocation#Named(String) named location} without a rebuild: change
     * to {@code Named("x")}, store, and bind {@code "x"} to the index's current directory for the next
     * start. From then on, the binding decides where the index lives. If the name is bound to a
     * directory without the index files instead, the graph is rebuilt there from the stored vectors.
     *
     * @param name     the name of the index
     * @param location the new location
     * @throws IllegalArgumentException if there is no index of that name
     * @throws IllegalStateException    if the index is in-memory, or the parent {@link GigaMap} is not
     *                                  mutable
     */
    public void changeIndexLocation(String name, IndexLocation location);

    /**
     * Accesses the indices table.
     *
     * @param logic the consumer logic
     */
    public void accessIndices(Consumer<? super XGettingTable<String, ? extends VectorIndex<E>>> logic);



    public interface Internal<E> extends VectorIndices<E>
    {
        public VectorIndex.Internal<E> internalGet(String indexName);

        /**
         * @return whether GigaMap.removeAll() / reindex() are waiting for the write locks of this group's indices,
         *         during which new searches wait (see {@code IndexGroup.Internal#internalTryLockExclusive})
         */
        public boolean internalIsExclusivePending();
    }


    /**
     * Category for vector indices, used to create VectorIndices instances.
     *
     * @param <E> the entity type
     */
    public interface Category<E> extends IndexCategory<E, VectorIndices<E>>
    {
        @Override
        public Class<VectorIndices<E>> indexType();


        public class Default<E> implements Category<E>
        {
            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public final Class<VectorIndices<E>> indexType()
            {
                return (Class)VectorIndices.class;
            }

            @Override
            public final VectorIndices<E> createIndexGroup(final GigaMap<E> gigaMap)
            {
                if(!(gigaMap instanceof GigaMap.Internal))
                {
                    throw new IllegalArgumentException("gigaMap must be a GigaMap.Internal instance");
                }

                return new VectorIndices.Default<>((GigaMap.Internal<E>)gigaMap);
            }
        }
    }


    public static <E> Category<E> Category()
    {
        return new Category.Default<>();
    }


    public final class Default<E> extends AbstractStateChangeFlagged implements Internal<E>, Closeable
    {
        private static final Logger LOG = LoggerFactory.getLogger(VectorIndices.class);

        static BinaryTypeHandler<Default<?>> provideTypeHandler()
        {
            return BinaryHandlerVectorIndicesDefault.New();
        }


        ///////////////////////////////////////////////////////////////////////////
        // instance fields //
        ////////////////////

        final GigaMap.Internal<E> parent;

        final EqHashTable<String, VectorIndex.Internal<E>> vectorIndices;

        // Set while GigaMap.removeAll() / reindex() wait for the indices' write locks, read by searches. Written
        // under the parent-map monitor.
        private transient volatile boolean exclusivePending;


        ///////////////////////////////////////////////////////////////////////////
        // constructors //
        /////////////////

        protected Default(final GigaMap.Internal<E> parent)
        {
            this(parent, EqHashTable.New(), true);
        }

        Default(
            final GigaMap.Internal<E>                        parent       ,
            final EqHashTable<String, VectorIndex.Internal<E>> vectorIndices,
            final boolean                                     stateChanged
        )
        {
            super(stateChanged);
            this.parent        = parent       ;
            this.vectorIndices = vectorIndices;
        }

        ///////////////////////////////////////////////////////////////////////////
        // methods //
        ////////////

        @Override
        public final GigaMap.Internal<E> parentMap()
        {
            return this.parent;
        }

        protected final EqHashTable<String, VectorIndex.Internal<E>> vectorIndices()
        {
            return this.vectorIndices;
        }

        /**
         * Guards structural mutations against a parent {@link GigaMap} that is currently not mutable, applying
         * the same classification an entity write applies (see
         * {@link GigaMap.Internal#internalEnsureMutability()}): an explicit read-only mark, an in-progress
         * iteration and a self-held reader fail fast, while readers open on other threads are waited out.
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
                this.parent.internalEnsureMutability();
            }
            catch(final IllegalStateException e)
            {
                throw new IllegalStateException("Cannot " + operation + ": the GigaMap is not mutable.", e);
            }
        }

        @Override
        public final void internalAdd(final long entityId, final E entity)
        {
            // in a finally, see internalUpdateIndices
            try
            {
                for(final VectorIndex.Internal<E> index : this.vectorIndices.values())
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
            // in a finally, see internalUpdateIndices
            try
            {
                for(final VectorIndex.Internal<E> index : this.vectorIndices.values())
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
        public void internalPrepareIndicesUpdate(final E replacedEntity)
        {
            // Vector indices don't need preparation for updates
        }

        @Override
        public final void internalUpdateIndices(
            final long                         entityId         ,
            final E                            replacedEntity   ,
            final E                            entity           ,
            final CustomConstraints<? super E> customConstraints
        )
        {
            // In a finally, like GigaIndices: an index that throws may have moved its persisted restart witness or
            // written its vector store before the failure, and the storer only descends to it through this mark.
            try
            {
                for(final VectorIndex.Internal<E> index : this.vectorIndices.values())
                {
                    index.internalUpdate(entityId, replacedEntity, entity);
                }
            }
            finally
            {
                this.markStateChangeChildren();
            }
        }

        @Override
        public void internalFinishIndicesUpdate()
        {
            // Nothing to clean up
        }

        /**
         * Best-effort over every index, like {@code GigaIndices#internalRemove} over the groups: an index that throws
         * must not keep its siblings from being cleaned up. That matters for GigaMap's rollback of a failed add,
         * where an index throws on purpose to retire the id (see {@code VectorIndex.Default#halfInsertedOrdinal}).
         * The first failure is rethrown at the end, later ones are suppressed.
         */
        @Override
        public final void internalRemove(final long entityId, final E entity)
        {
            RuntimeException first = null;
            try
            {
                for(final VectorIndex.Internal<E> index : this.vectorIndices.values())
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
            for(final VectorIndex.Internal<E> index : this.vectorIndices.values())
            {
                index.internalRemoveAll();
            }
            this.markStateChangeChildren();
        }

        /**
         * Rebuilds each index on its own, unlike the group default, whose fan-out per entity lets one throwing
         * vectorizer truncate every index of the group. A failing index stays as it was and does not keep the
         * others from being rebuilt; the first failure is rethrown at the end, later ones are suppressed.
         */
        @Override
        public void internalReindex(final GigaMap<E> parentMap)
        {
            RuntimeException first = null;
            try
            {
                for(final VectorIndex.Internal<E> index : this.vectorIndices.values())
                {
                    try
                    {
                        index.internalReindex();
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
        public boolean internalTryLockExclusive()
        {
            int locked = 0;
            try
            {
                for(final VectorIndex.Internal<E> index : this.vectorIndices.values())
                {
                    if(!index.internalTryLockExclusive())
                    {
                        // keep new searches out until the next try (see VectorIndex awaitNoPendingExclusive)
                        this.exclusivePending = true;
                        break;
                    }
                    locked++;
                }
            }
            finally
            {
                if(locked < this.vectorIndices.size())
                {
                    this.unlockFirst(locked);
                }
            }
            return locked == this.vectorIndices.size();
        }

        @Override
        public void internalUnlockExclusive()
        {
            this.unlockFirst(Math.toIntExact(this.vectorIndices.size()));
            this.exclusivePending = false;
        }

        @Override
        public void internalCancelExclusive()
        {
            this.exclusivePending = false;
        }

        @Override
        public boolean internalIsExclusivePending()
        {
            return this.exclusivePending;
        }

        private void unlockFirst(final int count)
        {
            int i = 0;
            for(final VectorIndex.Internal<E> index : this.vectorIndices.values())
            {
                if(i++ == count)
                {
                    break;
                }
                index.internalUnlockExclusive();
            }
        }

        @Override
        public VectorIndex<E> add(
            final String name,
            final VectorIndexConfiguration configuration,
            final Vectorizer<? super E> vectorizer
        )
        {
            synchronized(this.parentMap())
            {
                this.ensureMutable("add vector index \"" + name + "\"");

                return this.internalAddIndex(name, configuration, vectorizer);
            }
        }

        /**
         * Creates, back-fills and registers a single vector index without checking mutability. Callers must
         * have passed {@link #ensureMutable(String)} and must still hold the parent-map monitor.
         * <p>
         * The index is built completely before it is registered: it is back-filled from the entities the
         * parent map already holds while it is still absent from this group, and a back-fill that throws
         * discards it, leaving the group as it was. Registration itself runs no user code. With eventual
         * indexing only the vectors are computed before registration; the graph is built in the background
         * afterwards, as it is for any later add.
         */
        private VectorIndex<E> internalAddIndex(
            final String name,
            final VectorIndexConfiguration configuration,
            final Vectorizer<? super E> vectorizer
        )
        {
            this.validateIndexToAdd(name);

            final VectorIndex.Default<E> index = new VectorIndex.Default<>(
                this,
                name,
                true,
                configuration,
                vectorizer
            );

            final List<VectorEntry> backfilled;
            try
            {
                backfilled = index.internalBackfill();
            }
            catch(final Throwable t)
            {
                index.internalDiscard(t);
                throw t;
            }

            // Before registration, so that a failure to start the background managers registers nothing.
            index.internalActivate();
            this.internalAddVectorIndex(index);

            if(backfilled != null)
            {
                index.internalAddBackfilled(backfilled);
            }

            return index;
        }

        @Override
        public VectorIndex<E> ensure(
            final String name,
            final VectorIndexConfiguration configuration,
            final Vectorizer<? super E> vectorizer
        )
        {
            synchronized(this.parentMap())
            {
                VectorIndex<E> index = this.internalGet(name);
                if(index != null)
                {
                    // Already present: no structural change, so the mutability guard is deliberately not
                    // reached. This keeps ensure() a no-op on a read-only map.
                    warnIfLocationDiffers(index, configuration);
                    return index;
                }

                this.ensureMutable("add vector index \"" + name + "\"");

                // The guard may have released the parent-map monitor while waiting for foreign readers, so
                // another thread may have registered the index meanwhile. Re-check instead of letting
                // #validateIndexToAdd turn an idempotent ensure() into a "name already taken" failure.
                index = this.internalGet(name);

                return index != null
                    ? index
                    : this.internalAddIndex(name, configuration, vectorizer)
                ;
            }
        }

        /**
         * The stored location of an existing index wins, like every other stored setting. A different
         * location passed to {@code ensure} would otherwise be ignored silently, which is exactly how an
         * application ends up with its index files somewhere it did not expect.
         */
        private static void warnIfLocationDiffers(
            final VectorIndex<?>           index        ,
            final VectorIndexConfiguration configuration
        )
        {
            if(configuration == null || !configuration.onDisk() || !index.configuration().onDisk())
            {
                return;
            }
            final IndexLocation stored = index.configuration().indexLocation();
            final IndexLocation passed = configuration.indexLocation();
            if(!Objects.equals(stored, passed))
            {
                LOG.warn(
                    "Vector index '{}' exists with location {}; the passed location {} has no effect."
                    + " Use VectorIndices.changeIndexLocation to change it.",
                    index.name(), stored, passed
                );
            }
        }

        @Override
        public void changeIndexLocation(final String name, final IndexLocation location)
        {
            notNull(name);
            notNull(location);
            synchronized(this.parentMap())
            {
                // May release the monitor while waiting for foreign readers, so look the index up after it.
                this.ensureMutable("change the location of vector index \"" + name + "\"");

                final VectorIndex.Internal<E> index = this.internalGet(name);
                if(index == null)
                {
                    throw new IllegalArgumentException("No vector index named \"" + name + "\".");
                }
                final VectorIndexConfiguration current = index.configuration();
                if(!current.onDisk())
                {
                    throw new IllegalStateException(
                        "Vector index \"" + name + "\" is an in-memory index and has no index location."
                    );
                }
                if(location.equals(current.indexLocation()))
                {
                    return;
                }

                index.internalReplaceConfiguration(current.withIndexLocation(location));

                // A location change is no entity mutation, so nothing else flags the path from the map to
                // this index: the index marked itself; its group and the map's index groups must follow,
                // or the next store skips the new configuration.
                this.markStateChangeChildren();
                this.parent.internalReportIndexGroupStateChange(this);

                LOG.info(
                    "Vector index '{}': location changed from {} to {}, effective from the next load.",
                    name, current.indexLocation(), location
                );
            }
        }

        @Override
        public final VectorIndex<E> get(final String name)
        {
            synchronized(this.parentMap())
            {
                return this.internalGet(name);
            }
        }

        @Override
        public boolean removeIndex(final String name)
        {
            final VectorIndex.Internal<E> index;
            synchronized(this.parentMap())
            {
                this.ensureMutable("remove vector index \"" + name + "\"");

                index = this.vectorIndices.get(name);
                if(index == null)
                {
                    return false;
                }
            }
            // Close the index outside the parentMap monitor: index.close() acquires the builder write-lock,
            // while a background persist holds that lock and waits for the parentMap monitor — holding the
            // monitor across close() would dead-lock. Drop the index from the registry only after close()
            // succeeds, so a failing close leaves it registered (and re-closeable) rather than
            // detached-but-unclosed.
            index.close();
            synchronized(this.parentMap())
            {
                this.vectorIndices.removeFor(name);
                this.markStateChangeInstance();
                this.parent.internalReportIndexGroupStateChange(this);
            }
            return true;
        }

        /**
         * Closes all contained vector indices, releasing their native resources. Invoked when the
         * whole vector index group is removed via {@link GigaIndices#remove(IndexCategory)} and on storage
         * shutdown.
         * <p>
         * The indices are collected under the {@code parentMap} monitor but closed <b>outside</b> of it:
         * {@code index.close()} acquires the builder write-lock, while a background persist holds that lock
         * and waits for the {@code parentMap} monitor, so holding the monitor across {@code close()} would
         * dead-lock.
         */
        @Override
        public void close()
        {
            final BulkList<VectorIndex.Internal<E>> toClose;
            synchronized(this.parentMap())
            {
                toClose = BulkList.New(this.vectorIndices.values());
            }
            for(final VectorIndex.Internal<E> index : toClose)
            {
                index.close();
            }
        }

        @Override
        public final VectorIndex.Internal<E> internalGet(final String indexName)
        {
            return this.vectorIndices.get(indexName);
        }

        /**
         * Registers an index that is already fully built (see {@link #internalAddIndex}). Runs no user code
         * and changes nothing before the parent check has passed.
         */
        private void internalAddVectorIndex(final VectorIndex.Internal<E> index)
        {
            if(index.parent() != this)
            {
                throw new IllegalStateException(
                    "Inconsistent parent reference for index VectorIndex \"" + index.name() + "\"."
                );
            }

            this.vectorIndices.add(index.name(), index);
            this.markStateChangeInstance();
            this.parent.internalReportIndexGroupStateChange(this);
        }

        private void validateIndexToAdd(final String indexName)
        {
            validateIndexName(indexName);

            final VectorIndex<E> index = this.vectorIndices.get(indexName);
            if(index != null)
            {
                throw new RuntimeException("VectorIndex already registered for name \"" + index.name() + "\".");
            }
        }

        /**
         * Validates that an index name is safe for use as a filesystem filename prefix.
         * <p>
         * The name is used to create files like {@code {name}.graph} and {@code {name}.meta},
         * so it must be a valid filename on all supported operating systems.
         * <p>
         * Uses Java NIO's {@link java.nio.file.Path} for platform-specific validation.
         *
         * @param indexName the index name to validate
         * @throws IllegalArgumentException if the name is invalid
         */
        private static void validateIndexName(final String indexName)
        {
            if(indexName == null)
            {
                throw new IllegalArgumentException("Index name may not be null.");
            }

            if(indexName.isEmpty())
            {
                throw new IllegalArgumentException("Index name may not be empty.");
            }

            if(indexName.contains("/") || indexName.contains("\\"))
            {
                throw new IllegalArgumentException("Index name may not contain '/' or '\\' characters.");
            }

            if(indexName.length() > 200)
            {
                throw new IllegalArgumentException(
                    "Index name is too long (max 200 characters): " + indexName.length()
                );
            }

            // Use Java NIO Path validation - throws InvalidPathException for invalid filenames
            try
            {
                // Test that the name can be used as a filename (with extension)
                java.nio.file.Path.of(indexName + ".graph");
            }
            catch(final java.nio.file.InvalidPathException e)
            {
                throw new IllegalArgumentException(
                    "Index name contains invalid filesystem characters: \"" + indexName + "\" - " + e.getReason()
                );
            }
        }

        @Override
        public void clearStateChangeMarkers()
        {
            super.clearStateChangeMarkers();
            this.vectorIndices.values().iterate(VectorIndex.Internal::clearStateChangeMarkers);
        }

        @Override
        protected final void clearChildrenStateChangeMarkers()
        {
            this.vectorIndices.values().iterate(VectorIndex.Internal::clearStateChangeMarkers);
        }

        @Override
        protected final void storeChildren(final Storer storer)
        {
            synchronized(this.parentMap())
            {
                super.storeChildren(storer);
            }
        }

        @Override
        protected final void storeChangedChildren(final Storer storer)
        {
            for(final VectorIndex.Internal<E> index : this.vectorIndices.values())
            {
                storer.store(index);
            }
        }

        @Override
        public <I extends Consumer<? super VectorIndex<E>>> I iterate(final I iterator)
        {
            synchronized(this.parentMap())
            {
                for(final KeyValue<String, ? extends VectorIndex<E>> entry : this)
                {
                    iterator.accept(entry.value());
                }
            }

            return iterator;
        }

        @Override
        public final Iterator<KeyValue<String, ? extends VectorIndex<E>>> iterator()
        {
            synchronized(this.parentMap())
            {
                return new EntryIterator<>(this.vectorIndices.copy());
            }
        }

        @Override
        public void accessIndices(final Consumer<? super XGettingTable<String, ? extends VectorIndex<E>>> logic)
        {
            synchronized(this.parentMap())
            {
                logic.accept(this.vectorIndices);
            }
        }


        protected static final class EntryIterator<E, I extends VectorIndex<E>>
        implements Iterator<KeyValue<String, ? extends VectorIndex<E>>>
        {
            private final Iterator<KeyValue<String, I>> iterator;

            EntryIterator(final EqHashTable<String, I> vectorIndices)
            {
                super();
                this.iterator = vectorIndices.iterator();
            }

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
    }

}
