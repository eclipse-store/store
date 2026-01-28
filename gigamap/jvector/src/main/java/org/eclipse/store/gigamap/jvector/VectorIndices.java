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

import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.collections.types.XGettingTable;
import org.eclipse.serializer.collections.types.XIterable;
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.store.gigamap.types.*;

import java.util.Iterator;
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
     * Adds a vector index to this group.
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
     *
     * @param name          the name of the index
     * @param configuration the index configuration
     * @param vectorizer    the vectorizer
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
     * Accesses the indices table.
     *
     * @param logic the consumer logic
     */
    public void accessIndices(Consumer<? super XGettingTable<String, ? extends VectorIndex<E>>> logic);



    public interface Internal<E> extends VectorIndices<E>
    {
        public VectorIndex.Internal<E> internalGet(String indexName);
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


    public final class Default<E> extends AbstractStateChangeFlagged implements Internal<E>
    {
        static BinaryTypeHandler<Default<?>> provideTypeHandler()
        {
            return BinaryHandlerVectorIndicesDefault.New();
        }


        ///////////////////////////////////////////////////////////////////////////
        // instance fields //
        ////////////////////

        final GigaMap.Internal<E> parent;

        final EqHashTable<String, VectorIndex.Internal<E>> vectorIndices;


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

        @Override
        public final void internalAdd(final long entityId, final E entity)
        {
            for(final VectorIndex.Internal<E> index : this.vectorIndices.values())
            {
                index.internalAdd(entityId, entity);
            }
            this.markStateChangeChildren();
        }

        @Override
        public final void internalAddAll(final long firstEntityId, final Iterable<? extends E> entities)
        {
            for(final VectorIndex.Internal<E> index : this.vectorIndices.values())
            {
                index.internalAddAll(firstEntityId, entities);
            }
            this.markStateChangeChildren();
        }

        @Override
        public final void internalAddAll(final long firstEntityId, final E[] entities)
        {
            for(final VectorIndex.Internal<E> index : this.vectorIndices.values())
            {
                index.internalAddAll(firstEntityId, entities);
            }
            this.markStateChangeChildren();
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
            // For vector indices, update = remove old + add new
            for(final VectorIndex.Internal<E> index : this.vectorIndices.values())
            {
                index.internalRemove(entityId, replacedEntity);
                index.internalAdd(entityId, entity);
            }
            this.markStateChangeChildren();
        }

        @Override
        public void internalFinishIndicesUpdate()
        {
            // Nothing to clean up
        }

        @Override
        public final void internalRemove(final long entityId, final E entity)
        {
            for(final VectorIndex.Internal<E> index : this.vectorIndices.values())
            {
                index.internalRemove(entityId, entity);
            }
            this.markStateChangeChildren();
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

        @Override
        public VectorIndex<E> add(
            final String name,
            final VectorIndexConfiguration configuration,
            final Vectorizer<? super E> vectorizer
        )
        {
            synchronized(this.parentMap())
            {
                this.validateIndexToAdd(name);

                final VectorIndex.Internal<E> index = new VectorIndex.Default<>(
                    this,
                    name,
                    true,
                    configuration,
                    vectorizer
                );
                this.internalAddVectorIndex(index);

                return index;
            }
        }

        @Override
        public VectorIndex<E> ensure(
            final String name,
            final VectorIndexConfiguration configuration,
            final Vectorizer<? super E> vectorizer
        )
        {
            VectorIndex<E> index = this.get(name);
            if(index == null)
            {
                index = this.add(name, configuration, vectorizer);
            }
            return index;
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
        public final VectorIndex.Internal<E> internalGet(final String indexName)
        {
            return this.vectorIndices.get(indexName);
        }

        private void internalAddVectorIndex(final VectorIndex.Internal<E> index)
        {
            this.vectorIndices.add(index.name(), index);

            if(index.parent() != this)
            {
                throw new IllegalStateException(
                    "Inconsistent parent reference for index VectorIndex \"" + index.name() + "\"."
                );
            }

            this.markStateChangeInstance();

            // Index existing entities
            this.parent.iterateIndexed(index::internalAdd);
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
