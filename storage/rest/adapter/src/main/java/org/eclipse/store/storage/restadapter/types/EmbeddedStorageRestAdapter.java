package org.eclipse.store.storage.restadapter.types;

/*-
 * #%L
 * EclipseStore Storage REST Adapter
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

import static org.eclipse.serializer.util.X.notNull;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.persistence.types.PersistenceRootsView;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryAssembler;
import org.eclipse.store.storage.exceptions.StorageException;
import org.eclipse.store.storage.restadapter.exceptions.StorageRestAdapterException;
import org.eclipse.store.storage.types.StorageManager;
import org.eclipse.store.storage.types.StorageRawFileStatistics;

public interface EmbeddedStorageRestAdapter
{
	public ObjectDescription getStorageObject(long objectId);

	public ObjectDescription getConstant(long objectId);

	public List<ViewerRootDescription> getRoots();

	public ViewerRootDescription getRoot();

	public String getTypeDictionary();

	public StorageRawFileStatistics getFileStatistics();


	public static EmbeddedStorageRestAdapter New(final StorageManager storage)
	{
		notNull(storage);

		return new Default(
			ViewerBinaryPersistenceManager.New(storage),
			storage
		);
	}


	public static class Default implements EmbeddedStorageRestAdapter
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final ViewerBinaryPersistenceManager viewerPersistenceManager;
		private final StorageManager                 storageManager;

		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final ViewerBinaryPersistenceManager viewerPersistenceManager,
			final StorageManager                 storageManager
		)
		{
			this.viewerPersistenceManager = viewerPersistenceManager;
			this.storageManager           = storageManager;
		}


		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		/**
		 *
		 * Get an object's description by an Eclipse Store ObjectId.
		 *
		 * @param objectId the object id to look up
		 * @return description of the object
		 */
		@Override
		public ObjectDescription getStorageObject(final long objectId)
		{

			final Persistence.IdType idType = Persistence.IdType.determineFromValue(objectId);

			if(idType == Persistence.IdType.CID)
			{
				return this.getConstant(objectId);
			}

			try
			{
				return this.viewerPersistenceManager.getStorageObject(objectId);
			}
			//TODO will be a StorageException soon ...
			catch(final RuntimeException e)
			{
				if(e.getCause() instanceof StorageException)
				{
					throw new StorageRestAdapterException(e.getCause().getMessage());
				}
				throw e;
			}
		}

		/**
		 *
		 * Get java constants values.
		 *
		 * @param objectId the object id to look up
		 * @return the constants value as object
		 */
		@Override
		public ObjectDescription getConstant(final long objectId)
		{
			return this.viewerPersistenceManager.getStorageConstant(objectId);
		}

		/**
		 * Get all registered root elements of the current Eclipse Store instance.
		 *
		 * @return List of ViewerRootDescription objects
		 */
		@Override
		public List<ViewerRootDescription> getRoots()
		{
			final PersistenceObjectRegistry registry = this.storageManager.persistenceManager().objectRegistry();
			final PersistenceRootsView roots = this.storageManager.viewRoots();

			final List<ViewerRootDescription> rootDescriptions = new ArrayList<>();

			roots.iterateEntries((id, root) ->
			{
				rootDescriptions.add(new ViewerRootDescription(id, registry.lookupObjectId(root)));
			});

			return rootDescriptions;
		}

		/**
		 * Get the current root name and object id.
		 * <p>
		 * If no default root is registered the returned ViewerRootDescription
		 * will have a "null" string as name and objectId 0.
		 *
		 * @return ViewerRootDescription
		 */
		@Override
		public ViewerRootDescription getRoot()
		{
			final PersistenceObjectRegistry registry = this.storageManager.persistenceManager().objectRegistry();
			final PersistenceRootsView roots = this.storageManager.viewRoots();

			final Object defaultRoot = roots.rootReference().get();
			if(defaultRoot != null)
			{
				return new ViewerRootDescription(PersistenceRootsView.rootIdentifier(), registry.lookupObjectId(defaultRoot));
			}

			return new ViewerRootDescription(PersistenceRootsView.rootIdentifier(), 0);
		}

		@Override
		public String getTypeDictionary()
		{
			final PersistenceTypeDictionaryAssembler assembler = PersistenceTypeDictionaryAssembler.New();
			return assembler.assemble(this.storageManager.typeDictionary());
		}

		@Override
		public StorageRawFileStatistics getFileStatistics()
		{
			return this.storageManager.createStorageStatistics();
		}

	}

}
