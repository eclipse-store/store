package org.eclipse.store.storage.restadapter.types;

import java.util.function.BiConsumer;

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

import java.util.function.Consumer;

import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.collections.types.XGettingEnum;
import org.eclipse.serializer.collections.types.XGettingSequence;
import org.eclipse.serializer.collections.types.XGettingTable;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.binary.types.BinaryPersistence;
import org.eclipse.serializer.persistence.exceptions.PersistenceExceptionConsistency;
import org.eclipse.serializer.persistence.types.PersistenceLegacyTypeHandler;
import org.eclipse.serializer.persistence.types.PersistenceManager;
import org.eclipse.serializer.persistence.types.PersistenceRootsView;
import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinition;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionary;
import org.eclipse.serializer.persistence.types.PersistenceTypeHandler;
import org.eclipse.serializer.persistence.types.PersistenceTypeHandlerManager;
import org.eclipse.serializer.persistence.types.PersistenceTypeLink;
import org.eclipse.serializer.reference.Referencing;
import org.eclipse.serializer.reflect.XReflect;
import org.eclipse.serializer.typing.KeyValue;

public class ViewerBinaryTypeHandlerManager implements PersistenceTypeHandlerManager<Binary>, Referencing<PersistenceTypeHandlerManager<Binary>>
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final PersistenceTypeDictionary typeDictionary;
	private final EqHashTable<Long, PersistenceTypeHandler<Binary, ?>> viewerTypeHandlers = EqHashTable.New();
	private final XGettingSequence<? extends PersistenceTypeHandler<Binary, ?>> nativeHandlers;

	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public ViewerBinaryTypeHandlerManager(final PersistenceManager<Binary> persistenceManager)
	{
		super();

		this.typeDictionary = persistenceManager.typeDictionary();
		this.nativeHandlers = BinaryPersistence.createNativeHandlersValueTypes(this, null, null);

		//initialize generic handlers
		for (final PersistenceTypeHandler<Binary, ?> persistenceTypeHandler : this.nativeHandlers)
		{
			final PersistenceTypeDefinition typeDefinition = this.typeDictionary
				.lookupTypeByName(persistenceTypeHandler.typeName());
			if(typeDefinition != null)
			{
				persistenceTypeHandler.initialize(typeDefinition.typeId());
			}
		}

		this.buildTypeHandlerDictionary();
	}

	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	private void buildTypeHandlerDictionary()
	{
		final XGettingTable<Long, PersistenceTypeDefinition> orginialTypes = this.typeDictionary.allTypeDefinitions();

		for (final KeyValue<Long, PersistenceTypeDefinition> keyValue : orginialTypes)
		{
			this.viewerTypeHandlers.add(keyValue.key(), this.deriveTypeHandler(keyValue.key()));
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private PersistenceTypeHandler<Binary, ObjectDescription> deriveTypeHandler(final long typeId)
	{
		final PersistenceTypeDefinition persistenceTypeDef = this.typeDictionary.lookupTypeById(typeId);
		final PersistenceTypeHandler<Binary, ?> nativeHandler = this.nativeHandlers.search(t->t.typeId() == typeId );

		final ViewerBinaryTypeHandlerGeneric genericHandler = new ViewerBinaryTypeHandlerGeneric(persistenceTypeDef);

		if(nativeHandler != null)
		{
			if(persistenceTypeDef.type().isArray())
			{
				if(persistenceTypeDef.type().getComponentType().isPrimitive())
				{
					return new ViewerBinaryTypeHandlerNativeArray(nativeHandler);
				}
			}

			return new ViewerBinaryTypeHandlerBasic(nativeHandler, genericHandler);
		}

		return genericHandler;
	}

	private PersistenceTypeHandler<Binary, ?> createTypeHandler(final long typeId)
	{
		this.viewerTypeHandlers.add(typeId, this.deriveTypeHandler(typeId));
		return this.viewerTypeHandlers.get(typeId);
	}

	@Override
	public long currentTypeId()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void updateCurrentHighestTypeId(final long highestTypeId)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean registerType(final long typeId, final Class<?> type) throws PersistenceExceptionConsistency
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean registerTypes(final Iterable<? extends PersistenceTypeLink> types) throws PersistenceExceptionConsistency
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public long lookupTypeId(final Class<?> type)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> Class<T> lookupType(final long typeId)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean validateTypeMapping(final long typeId, final Class<?> type) throws PersistenceExceptionConsistency
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean validateTypeMappings(final Iterable<? extends PersistenceTypeLink> mappings)
			throws PersistenceExceptionConsistency
	{
		throw new UnsupportedOperationException();
	}
	
	@Override
	public <T> boolean registerTypeHandler(final Class<T> type, final PersistenceTypeHandler<Binary, ? super T> typeHandler)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> boolean registerTypeHandler(final PersistenceTypeHandler<Binary, T> typeHandler)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public long registerTypeHandlers(final Iterable<? extends PersistenceTypeHandler<Binary, ?>> typeHandlers)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean registerLegacyTypeHandler(final PersistenceLegacyTypeHandler<Binary, ?> legacyTypeHandler)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public <C extends Consumer<? super PersistenceTypeHandler<Binary, ?>>> C iterateTypeHandlers(final C iterator)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public <C extends Consumer<? super PersistenceLegacyTypeHandler<Binary, ?>>> C iterateLegacyTypeHandlers(final C iterator)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> PersistenceTypeHandler<Binary, T> lookupTypeHandler(final T instance)
	{
		return this.lookupTypeHandler(XReflect.getClass(instance));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> PersistenceTypeHandler<Binary, T> lookupTypeHandler(final Class<T> type)
	{
		return (PersistenceTypeHandler<Binary, T>)this.viewerTypeHandlers.values().search(
			v -> v.typeName().equals(type.getName())
		);
	}

	@Override
	public PersistenceTypeHandler<Binary, ?> lookupTypeHandler(final long typeId)
	{
		PersistenceTypeHandler<Binary, ?> handler = this.viewerTypeHandlers.get(typeId);
		
		if(handler==null)
		{
			handler = this.createTypeHandler(typeId);
		}
		return handler;
	}

	@Override
	public <T> PersistenceTypeHandler<Binary, T> ensureTypeHandler(final T instance)
	{
		return this.lookupTypeHandler(instance);
	}

	@Override
	public <T> PersistenceTypeHandler<Binary, T> ensureTypeHandler(final Class<T> type)
	{
		return this.lookupTypeHandler(type);
	}

	@Override
	public <T> PersistenceTypeHandler<Binary, T> ensureTypeHandler(final PersistenceTypeDefinition typeDefinition)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void ensureTypeHandlers(final XGettingEnum<PersistenceTypeDefinition> typeDefinitions)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void ensureTypeHandlersByTypeIds(final XGettingEnum<Long> typeIds)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public PersistenceTypeHandlerManager<Binary> initialize()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void update(final PersistenceTypeDictionary typeDictionary, final long highestTypeId)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public PersistenceTypeDictionary typeDictionary()
	{
		return this.typeDictionary;
	}

	@Override
	public long ensureTypeId(final Class<?> type)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public Class<?> ensureType(final long typeId)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void validateTypeHandler(final PersistenceTypeHandler<Binary, ?> typeHandler)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void checkForPendingRootInstances()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void checkForPendingRootsStoring(final PersistenceStoring storingCallback)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void clearStorePendingRoots()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public PersistenceTypeHandlerManager<Binary> get()
	{
		return this;
	}

	@Override
	public <T> PersistenceLegacyTypeHandler<Binary, ? super T> ensureLegacyTypeHandler
	(
			final PersistenceTypeDefinition legacyTypeDefinition,
			final PersistenceTypeHandler<Binary, ? super T> currentTypeHandler
	)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void iteratePerIds(final BiConsumer<Long, ? super Class<?>> consumer)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public PersistenceRootsView viewRoots()
	{
		throw new UnsupportedOperationException();
	}

}
