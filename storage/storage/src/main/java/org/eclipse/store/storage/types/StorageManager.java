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
 * Java application that uses it. It is used for starting and stopping storage management threads,
 * calling storage-level utility functionality like clearing the low-level data cache, cleaning up / condensing
 * storage files or calling the storage-level garbage collector to remove data that has become unreachable in the
 * entity graph. This type also allows querying the used {@link StorageConfiguration} or the
 * {@link StorageTypeDictionary} that defines the persistent structure of all handled types.
 * <p>
 * For most cases, only the methods {@link #root()}, {@link #setRoot(Object)}, {@link #storeRoot()},
 * {@link #start()} and {@link #shutdown()} are important. Everything else is used for more or less advanced
 * purposes and should only be used with good knowledge about the effects caused by it.
 * <p>
 * A {@link StorageManager} instance is also implicitly a {@link StorageConnection}, so that developers don't
 * need to care about connections at all if a single connection suffices.
 *
 * <h2>Usage rules every caller must know</h2>
 * <ul>
 *   <li><b>The modified object must be stored.</b> The default storer is lazy: it persists only instances
 *       that are not yet known to the persistent context. An already-persisted object whose fields you
 *       mutated will <i>not</i> be picked up by storing one of its ancestors. Call {@code store(Object)}
 *       on the actually modified instance, or use {@link #createEagerStorer()} when you need recursive
 *       storing of already-known instances.</li>
 *   <li><b>Mutation and storing must happen under the same lock.</b> {@link StorageManager} does not
 *       synchronize the in-memory object graph for you. In a multi-threaded application, the modification
 *       of an object and the corresponding {@code store(...)} call must be executed atomically with
 *       respect to other threads &mdash; otherwise other threads may observe partially modified state, or
 *       the persisted data may not reflect the intended change.</li>
 *   <li><b>Crash safety.</b> Once a {@code store(...)} call returns, the data it stored is guaranteed to
 *       be physically written to the underlying storage layer. A process crash before that point causes
 *       the next {@link #start()} to truncate the partially written store and resume from the last fully
 *       persisted state. As a consequence, calling {@link #shutdown()} is not required for data
 *       integrity; see {@link #shutdown()} for the cases in which it is actually meaningful.</li>
 *   <li><b>One running {@link StorageManager} per data location.</b> Any number of {@link StorageManager}
 *       instances may run in the same JVM, but no two running instances may target the same data
 *       location. The read-only mode (see {@code StorageWriteControllerReadOnlyMode}) lifts this for
 *       read access, with the limitations documented there.</li>
 * </ul>
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
	 * @return the current {@link StorageTypeDictionary}.
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
	 * Note that while it makes sense on an architectural level to have a connecting mechanism between
	 * application logic and storage level, there is currently no need to create additional connections beyond the
	 * intrinsic one held inside a {@link StorageManager} instance for typical use cases. Just use the
	 * {@link StorageManager} instance itself.<br>
	 * Creating a dedicated connection is only relevant for advanced scenarios where you need a
	 * connection-scoped {@link org.eclipse.serializer.persistence.types.PersistenceManager} (e.g. for
	 * isolating a parallel storer with its own object registry view).
	 *
	 * @return a new {@link StorageConnection} instance.
	 */
	public StorageConnection createConnection();

	/**
	 * Returns the current root object of the persistent object graph managed by this instance.
	 * The root object is the entry point for accessing the graph of persisted objects.
	 * <p>
	 * Internally there are two distinct root mechanisms: a <i>default root</i> (set via
	 * {@link #setRoot(Object)} after the manager has been started) and a <i>custom root</i> (registered
	 * directly at database setup, e.g. via {@code EmbeddedStorage.start(myRoot)}). This method
	 * transparently returns whichever variant is in use.
	 * <p>
	 * The return type is {@link Object} by design &mdash; the storage layer cannot know the concrete root
	 * type, and adding a type parameter just for this would be more complication than benefit. The
	 * {@code <R>} type parameter is therefore an unchecked convenience cast; prefer keeping a typed
	 * reference to your root in application code instead of relying on it.
	 *
	 * @param <R> the type of the root object (unchecked convenience cast)
	 * @return the root object of the persistent object graph, or {@code null} if no root is currently set
	 *
	 * @see #setRoot(Object)
	 * @see #storeRoot()
	 */
	public <R> R root();
	
	/**
	 * Sets the passed instance as the new root for the persistent object graph.<br>
	 * Note that this will replace the old root reference, potentially making the previously reachable
	 * graph unreachable and thus eligible for storage-level garbage collection.
	 * <p>
	 * <b>This change is in-memory only.</b> The new root reference is not persisted until
	 * {@link #storeRoot()} is called. The canonical pattern is therefore:
	 * <pre>{@code
	 * storageManager.setRoot(newRoot);
	 * storageManager.storeRoot();
	 * }</pre>
	 * As with any state-changing operation, the {@code setRoot(...)} / {@code storeRoot()} pair must be
	 * executed under the same lock as any concurrent reads or modifications of the graph (see the
	 * class-level javadoc).
	 *
	 * @param <R> the type of the root object
	 * @param newRoot the new root instance to be set.
	 *
	 * @return the passed {@literal newRoot} to allow fluent usage of this method.
	 *
	 * @see #storeRoot()
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
	 * <p>
	 * As with any storing operation, the modification that prompted this call and the call itself must be
	 * executed under the same lock as any concurrent access to the affected part of the object graph (see
	 * the class-level javadoc).
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
	 * <p>
	 * Behavior:
	 * <ul>
	 *   <li>If the storage is not yet running, it is started &mdash; <i>before</i> the supplier is invoked.
	 *       This means the storage threads are running even if the supplier ultimately returns
	 *       {@code null} and this method throws.</li>
	 *   <li>If no root is currently set, the {@code initialRootSupplier} is invoked, the supplied root is
	 *       set via {@link #setRoot(Object)} and persisted via {@link #storeRoot()}.</li>
	 *   <li>If a root is already set (loaded from the storage on start), the supplier is <i>not</i>
	 *       invoked and {@link #storeRoot()} is <i>not</i> called.</li>
	 * </ul>
	 * The supplier may not return {@code null} on the initialization branch &mdash; doing so triggers an
	 * {@link IllegalArgumentException} after the storage has already been started.
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
	 * Returns a read-only view on all technical root instances registered in this {@link StorageManager}.<br>
	 * See the description in {@link PersistenceRootsView} for details.
	 * <p>
	 * Most applications will not need this method &mdash; use {@link #root()} for the application's root.
	 * This view is useful for advanced scenarios such as inspecting registered constants/JVM-implicit
	 * roots, tooling, or migration logic that needs to enumerate all registered root references.
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
