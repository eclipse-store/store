package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.persistence.types.PersistenceRootsView;
import org.eclipse.serializer.persistence.types.Storer;

import java.util.Objects;
import java.util.function.Supplier;


/**
 * Central managing type for a native Java database's storage layer.
 * <p>
 * For all intents and purposes, a {@link StorageManager} instance represents the storage of a database in the
 * Java application that uses it. It is used for starting and stopping storage managements threads,
 * call storage-level utility functionality like clearing the low-level data cache, cleaning up / condensing
 * storage files or calling the storage-level garbage collector to remove data that has become unreachable in the
 * entity graph. This type also allows querying the used {@link StorageConfiguration} or the
 * {@link StorageTypeDictionary} that defines the persistent structure of all handled types.
 * <p>
 * For the most cases, only the methods {@link #root()}, {@link #setRoot(Object)}, {@link #start()} and
 * {@link #shutdown()} are important. Everything else is used for more or less advanced purposes and should only be used
 * with good knowledge about the effects caused by it.
 * <p>
 * A {@link StorageManager} instance is also implicitly a {@link StorageConnection}, so that developers don't
 * need to care about connections at all if a single connection suffices.
 *
 */
public interface StorageManager extends StorageController, StorageConnection, DatabasePart
{
	/**
	 * Returns the {@link StorageConfiguration} used to initialize this {@link StorageManager} instance.
	 * 
	 * @return the used configuration.
	 */
	public StorageConfiguration configuration();
	
	/**
	 * Returns the {@link StorageTypeDictionary} that contains a complete list of types currently known to /
	 * handled by the storage represented by this {@link StorageManager} instance. This list grows dynamically
	 * as so far unknown types are discovered, analyzed, mapped and added on the fly by a store.
	 * 
	 * @return thr current {@link StorageTypeDictionary}.
	 */
	public StorageTypeDictionary typeDictionary();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public StorageManager start();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean shutdown();

	/**
	 * Creates a new {@link StorageConnection} instance. See the type description for details.<br>
	 * Not that while it makes sense on an architectural level to have a connecting mechanism between
	 * application logic and storage level, there is currently no need to create additional connections beyond the
	 * intrinsic one held inside a {@link StorageManager} instance. Just use it instead.
	 * 
	 * @return a new {@link StorageConnection} instance.
	 */
	public StorageConnection createConnection();

	/**
	 * Returns the current root object of the persistent object graph managed by this instance.
	 * The root object is the entry point for accessing the graph of persisted objects.
	 *
	 * @param <R> the type of the root object
	 * @return the root object of the persistent object graph, or null if no root is currently set
	 */
	public <R> R root();
	
	/**
	 * Sets the passed instance as the new root for the persistent object graph.<br>
	 * Note that this will replace the old root instance, potentially resulting in wiping the whole database.
	 *
	 * @param <R> the type of the root object
	 * @param newRoot the new root instance to be set.
	 * 
	 * @return the passed {@literal newRoot} to allow fluent usage of this method.
	 */
	public <R> R setRoot(R newRoot);
	
	/**
	 * Stores the registered root instance (as returned by {@link #root()}) using the default storing logic
	 * by calling {@link #createStorer()} to create the {@link Storer} to be used.
	 * <p>
	 * <b>Intended use cases.</b> This method is only meaningful in two situations:
	 * <ul>
	 *   <li>The root reference itself has been replaced via {@link #setRoot(Object)}.</li>
	 *   <li>The root is being stored for the first time on an otherwise empty storage.</li>
	 * </ul>
	 * <p>
	 * <b>Common pitfall.</b> {@code storeRoot()} is <i>not</i> a "save everything" operation. It does
	 * <b>not</b> persist arbitrary modifications made somewhere deep in the object graph. The default
	 * storer (see {@link #createLazyStorer()}) only stores instances that are not yet known to the
	 * persistent context (i.e. that do not yet have an objectId registered in the
	 * {@link PersistenceObjectRegistry}). Already-persisted objects whose fields have been mutated are
	 * therefore skipped, and such changes will silently not be reflected in the storage. To persist
	 * modifications to an existing object, call {@code store(Object)} on the actually modified
	 * instance (or a suitable ancestor that owns the changed reference) instead.
	 *
	 * @return the root instance's objectId.
	 *
	 * @see #setRoot(Object)
	 * @see #createStorer()
	 * @see #createLazyStorer()
	 */
	public long storeRoot();

	/**
	 * Ensures that the root object of the persistent object graph is initialized and available.
	 * If the storage is not running, it starts the storage. If the root object is not set, it uses
	 * the given supplier to provide the initial root and stores it. Throws an exception if the
	 * initial root provided by the supplier is null.
	 *
	 * @param <R> the type of the root object
	 * @param initialRootSupplier a supplier that provides the initial root object if it is not already set
	 * @return the root object of the persistent object graph, cast to the specified type
	 * @throws NullPointerException if {@code initialRootSupplier} is null
	 * @throws IllegalArgumentException if the supplied initial root is null
	 */
	public default <R> R ensureRoot(final Supplier<R> initialRootSupplier)
	{
		Objects.requireNonNull(initialRootSupplier, "initialRootSupplier must not be null");

		if (!this.isRunning())
		{
			this.start();
		}

		if (this.root() == null)
		{
			final R initialRoot = initialRootSupplier.get();
			if(initialRoot == null)
			{
				throw new IllegalArgumentException("Initial root must not be null");
			}
			this.setRoot(initialRoot);
			this.storeRoot();
		}
		return this.root();
	}
	
	/**
	 * Returns a read-only view on all technical root instance registered in this {@link StorageManager} instance.<br>
	 * See the description in {@link PersistenceRootsView} for details.
	 * 
	 * @return a new {@link PersistenceRootsView} instance allowing to iterate all technical root instances.
	 */
	public PersistenceRootsView viewRoots();
	
	/**
	 * Returns the {@link Database} instance this {@link StorageManager} is associated with.
	 * See its description for details.
	 * 
	 * @return the associated {@link Database} instance.
	 */
	public Database database();
	
	/**
	 * Alias for {@code return this.database().databaseName();}
	 * 
	 */
	@Override
	public default String databaseName()
	{
		return this.database().databaseName();
	}

}
