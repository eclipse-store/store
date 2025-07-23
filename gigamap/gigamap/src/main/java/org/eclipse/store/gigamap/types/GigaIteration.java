package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.util.function.Consumer;


/**
 * GigaIteration is a class that extends {@link AbstractGigaIterating} to provide
 * advanced iteration capabilities using bitmap results within a specified range of IDs.
 * It allows for efficient iteration and processing of elements resolved through a
 * {@link BitmapResult.Resolver} and includes functionality to apply a consumer action
 * on the resolved elements.
 *
 * @param <E> the type of elements managed and iterated through this class
 */
public final class GigaIteration<E> extends AbstractGigaIterating<E>
{
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	GigaIteration(
		final BitmapResult.Resolver<E> resolver,
		final long                     idStart ,
		final long                     idBound ,
		final BitmapResult[]           results
	)
	{
		super(resolver, idStart, idBound, results);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
		
	final void execute(final Consumer<? super E> consumer)
	{
		final BitmapResult.Resolver<E> resolver = this.resolver;
		
		long bitmapValue = this.currentBitmapValue;
		long valueBaseId = this.bitValBaseId;
		for(int bitPosition = -1;;)
		{
			do
			{
				if(++bitPosition >= Long.SIZE)
				{
					if(!this.scrollToNextBitmapValue())
					{
						return;
					}
					bitmapValue = this.currentBitmapValue;
					valueBaseId = this.bitValBaseId;
					bitPosition = 0;
				}
			}
			while((bitmapValue & 1L<<bitPosition) == 0L);

			// Null check filters out null entries when using a top-level not condition. No performance impact.
			final E entity = resolver.get(valueBaseId + bitPosition);
			if(entity == null)
			{
				continue;
			}
			consumer.accept(entity);
		}
	}
				
}
