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

import org.eclipse.serializer.collections.EqHashTable;

/**
 * Process-wide registry of {@link Database} instances keyed by their {@link Database#databaseName() name}.
 * <p>
 * Every running database in an EclipseStore JVM is registered with a {@link Databases} instance so that
 * its name can be enforced as a unique identifier and so that other components can look up the database
 * handle by name. The {@link #get() default singleton} returned by {@link Databases#get()} is the
 * registry that {@code EmbeddedStorageFoundation} uses by default; custom registries created via
 * {@link Databases#New()} are useful when multiple isolated database namespaces are needed within the
 * same JVM (for example in test setups).
 *
 * @see Database
 * @see DatabasePart
 */
public interface Databases
{
	/**
	 * Returns the {@link Database} registered under the passed name, or {@code null} if no database
	 * with that name has been registered yet.
	 *
	 * @param databaseName the identifying name of the database to look up.
	 *
	 * @return the registered {@link Database}, or {@code null} if none is registered for the name.
	 */
	public Database get(String databaseName);

	/**
	 * Returns the {@link Database} registered under the passed name, creating and registering a new
	 * storage-less entry if no database with that name exists yet.
	 * <p>
	 * If an entry already exists, this method additionally guarantees via
	 * {@link Database#guaranteeNoActiveStorage()} that no running {@link StorageManager} is currently
	 * associated with it. This protects against starting a second storage manager for the same data
	 * location through this registry path.
	 *
	 * @param databaseName the identifying name of the database to obtain.
	 *
	 * @return a {@link Database} entry that is guaranteed to currently have no active storage attached.
	 */
	public Database ensureStoragelessDatabase(String databaseName);



	/**
	 * Returns the JVM-wide default {@link Databases} singleton.
	 * <p>
	 * This is the registry used by the embedded-storage default wiring; in most applications it is the
	 * only registry that will ever be needed.
	 *
	 * @return the singleton {@link Databases} instance.
	 */
	public static Databases get()
	{
		return Static.get();
	}

	/**
	 * Holder class for the {@link Databases} singleton returned by {@link Databases#get()}.
	 */
	public final class Static
	{
		private static final Databases SINGLETON = Databases.New();

		static Databases get()
		{
			return SINGLETON;
		}

	}


	/**
	 * Pseudo-constructor method to create a fresh, empty {@link Databases} registry.
	 * <p>
	 * The returned registry is independent of the {@link #get() default singleton} and does not share
	 * any registered entries with it.
	 *
	 * @return a new, empty {@link Databases} instance.
	 */
	public static Databases New()
	{
		return new Databases.Default(EqHashTable.New());
	}
	
	/**
	 * Default in-memory implementation of {@link Databases} backed by an
	 * {@link EqHashTable} keyed by {@link Database#databaseName() database name}. All mutating operations
	 * are synchronized to make concurrent registration calls safe.
	 */
	public final class Default implements Databases
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final EqHashTable<String, Database> databases;

		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default(final EqHashTable<String, Database> databases)
		{
			super();
			this.databases = databases;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final synchronized Database get(final String databaseName)
		{
			return this.databases.get(databaseName);
		}

		@Override
		public final synchronized Database ensureStoragelessDatabase(final String databaseName)
		{
			Database database = this.get(databaseName);
			if(database != null)
			{
				database.guaranteeNoActiveStorage();
			}
			else
			{
				database = Database.New(databaseName);
				this.databases.add(databaseName, database);
			}
			
			return database;
		}
		
	}
	
}
