
package org.eclipse.store.storage.restclient.app.ui;

/*-
 * #%L
 * EclipseStore Storage REST Client App
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

import java.util.Comparator;
import java.util.HashSet;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery;

import org.eclipse.store.storage.restclient.app.types.ApplicationErrorHandler;
import org.eclipse.store.storage.restclient.types.StorageView;
import org.eclipse.store.storage.restclient.types.StorageViewElement;


public interface StorageViewDataProvider<F> extends HierarchicalDataProvider<StorageViewElement, F>
{
	public static <F> StorageViewDataProvider<F> New(final StorageView storageView)
	{
		notNull(storageView);
		return new Default<>(() -> storageView.root());
	}

	public static <F> StorageViewDataProvider<F> New(final StorageViewElement root)
	{
		notNull(root);
		return new Default<>(() -> root);
	}


	public static class Default<F>
		extends AbstractBackEndHierarchicalDataProvider<StorageViewElement, F>
		implements StorageViewDataProvider<F>
	{
		private final Supplier<StorageViewElement> rootSupplier;
		private final HashSet<StorageViewElement>  dirtyElements = new HashSet<>();

		Default(final Supplier<StorageViewElement> rootSupplier)
		{
			super();
			this.rootSupplier = rootSupplier;
		}

		@Override
		public boolean hasChildren(
			final StorageViewElement item
		)
		{
			try
			{
				return item.hasMembers();
			}
			catch(final Exception e)
			{
				ApplicationErrorHandler.handle(e);
				return false;
			}
		}

		@Override
		public int getChildCount(
			final HierarchicalQuery<StorageViewElement, F> query
		)
		{
			try
			{
				final StorageViewElement parent = query.getParent();
				return parent == null
					? 1 // root
					: parent.hasMembers()
						? parent.members(this.dirtyElements.remove(parent)).size()
						: 0;
			}
			catch(final Exception e)
			{
				ApplicationErrorHandler.handle(e);
				return 0;
			}
		}

		@Override
		protected Stream<StorageViewElement> fetchChildrenFromBackEnd(
			final HierarchicalQuery<StorageViewElement, F> query
		)
		{
			try
			{
				final StorageViewElement parent = query.getParent();
				Stream<StorageViewElement> stream = parent == null
					? Stream.of(this.rootSupplier.get())
					: parent.members(this.dirtyElements.remove(parent)).stream();
				final Comparator<StorageViewElement> comparator = query.getInMemorySorting();
				if(comparator != null)
				{
					stream = stream.sorted(comparator);
				}
				return stream
					.skip(query.getOffset())
					.limit(query.getLimit());
			}
			catch(final Exception e)
			{
				ApplicationErrorHandler.handle(e);
				return Stream.empty();
			}
		}

		@Override
		public void refreshItem(
			final StorageViewElement item,
			final boolean refreshChildren
		)
		{
			try
			{
				this.dirtyElements.add(item);

				super.refreshItem(item, refreshChildren);
			}
			catch(final Exception e)
			{
				ApplicationErrorHandler.handle(e);
			}
		}

	}

}
