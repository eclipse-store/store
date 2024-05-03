
package org.eclipse.store.examples.extensionwrapper;

/*-
 * #%L
 * EclipseStore Example Extension Wrapper
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

import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceObjectManager;
import org.eclipse.serializer.persistence.types.PersistenceStorer;
import org.eclipse.serializer.persistence.types.PersistenceTarget;
import org.eclipse.serializer.persistence.types.PersistenceTypeHandlerManager;
import org.eclipse.serializer.persistence.types.Persister;
import org.eclipse.serializer.reference.ObjectSwizzling;
import org.eclipse.serializer.util.BufferSizeProviderIncremental;


/**
 * Extension for {@link PersistenceStorer} which adds logic to store operations
 *
 */
public class PersistenceStorerExtension extends PersistenceStorerWrapper
{
	public PersistenceStorerExtension(final PersistenceStorer delegate)
	{
		super(delegate);
	}
	
	private void beforeStoreObject(final Object instance)
	{
		System.out.println("Storing " + instance.getClass().getName() + "@" + System.identityHashCode(instance));
	}
	
	@Override
	public long store(final Object instance)
	{
		this.beforeStoreObject(instance);
		
		return super.store(instance);
	}
	
	@Override
	public void storeAll(final Iterable<?> instances)
	{
		instances.forEach(this::beforeStoreObject);
		
		super.storeAll(instances);
	}
	
	@Override
	public long[] storeAll(final Object... instances)
	{
		for(final Object instance : instances)
		{
			this.beforeStoreObject(instance);
		}
		
		return super.storeAll(instances);
	}
	
	
	
	
	public static class Creator implements PersistenceStorer.Creator<Binary>
	{
		private final PersistenceStorer.Creator<Binary> delegate;

		public Creator(PersistenceStorer.Creator<Binary> delegate)
		{
			super();
			this.delegate = delegate;
		}
		
		@Override
		public PersistenceStorer createLazyStorer(
			final PersistenceTypeHandlerManager<Binary> typeManager       ,
			final PersistenceObjectManager<Binary>      objectManager     ,
			final ObjectSwizzling                       objectRetriever   ,
			final PersistenceTarget<Binary>             target            ,
			final BufferSizeProviderIncremental         bufferSizeProvider,
			final Persister                             persister
		)
		{
			return new PersistenceStorerExtension(
				this.delegate.createLazyStorer(typeManager, objectManager, objectRetriever, target, bufferSizeProvider, persister)
			);
		}

		@Override
		public PersistenceStorer createEagerStorer(
			final PersistenceTypeHandlerManager<Binary> typeManager       ,
			final PersistenceObjectManager<Binary>      objectManager     ,
			final ObjectSwizzling                       objectRetriever   ,
			final PersistenceTarget<Binary>             target            ,
			final BufferSizeProviderIncremental         bufferSizeProvider,
			final Persister                             persister
		)
		{
			return new PersistenceStorerExtension(
				this.delegate.createEagerStorer(typeManager, objectManager, objectRetriever, target, bufferSizeProvider, persister)
			);
		}
		
		
	}
	
}
