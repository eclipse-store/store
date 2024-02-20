
package org.eclipse.store.integrations.cdi.types.extension;

/*-
 * #%L
 * EclipseStore Integrations CDI 4
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */


import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.eclipse.store.integrations.cdi.exceptions.CDIExceptionStorage;
import org.eclipse.store.integrations.cdi.Storage;
import org.eclipse.store.integrations.cdi.types.config.StorageManagerInitializer;
import org.eclipse.serializer.reflect.XReflect;
import org.eclipse.store.storage.types.StorageManager;


/**
 * Storage Discovery Bean to CDI extension to register an entity with {@link Storage}
 * annotation.
 */
class StorageBean<T> extends AbstractBean<T>
{
	private final Class<T>        type      ;
	private final Set<Type>       types     ;
	private final Set<Annotation> qualifiers;
	
	protected StorageBean(final BeanManager beanManager
			, final Class<T> type
			, final Set<InjectionPoint> injectionPoints
	)
	{
		super(beanManager, injectionPoints);
		this.type       = type;
		this.types      = Collections.singleton(type);
		this.qualifiers = new HashSet<>();
		this.qualifiers.add(new Default.Literal());
		this.qualifiers.add(new Any.Literal());
	}
	
	@Override
	public Class<T> getBeanClass()
	{
		return this.type;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public T create(final CreationalContext<T> context)
	{
		final StorageManager manager = this.getInstance(StorageManager.class);
		final Object         root    = manager.root();
		T                    entity;
		if (Objects.isNull(root))
		{
			entity = XReflect.defaultInstantiate(this.type);
			manager.setRoot(entity);
			manager.storeRoot();
		}
		else
		{
			if (this.type.isInstance(root))
			{
				entity = (T) root;
			}
			else
			{
				throw new CDIExceptionStorage(this.type, root.getClass());
			}
		}
		this.injectDependencies(entity);

		final Set<Bean<?>> initializerBeans = this.beanManager.getBeans(StorageManagerInitializer.class);
		for (final Bean<?> initializerBean : initializerBeans)
		{
			final StorageManagerInitializer initializer = (StorageManagerInitializer) this.beanManager.getReference(initializerBean
					, initializerBean.getBeanClass()
					, this.beanManager.createCreationalContext(initializerBean));

			initializer.initialize(manager);
		}
		return entity;
	}

	@Override
	public Set<Class<? extends Annotation>> getStereotypes()
	{
		return Collections.singleton(Storage.class);
	}
	
	@Override
	public Set<Type> getTypes()
	{
		return this.types;
	}
	
	@Override
	public Set<Annotation> getQualifiers()
	{
		return this.qualifiers;
	}
	
	@Override
	public String getId()
	{
		return this.type.getName() + " @Storage";
	}
	
	@Override
	public String toString()
	{
		return "StorageBean{"
			+
			"type="
			+ this.type
			+
			", types="
			+ this.types
			+
			", qualifiers="
			+ this.qualifiers
			+
			'}';
	}
}
