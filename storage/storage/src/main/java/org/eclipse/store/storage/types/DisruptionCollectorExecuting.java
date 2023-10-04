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

import static org.eclipse.serializer.util.X.notNull;

import java.util.function.Supplier;

import org.eclipse.serializer.collections.types.XCollection;
import org.eclipse.serializer.functional.ThrowingProcedure;


public interface DisruptionCollectorExecuting<E> extends DisruptionCollector
{
	public void executeOn(final E element);
	
	
	
	public static <E> DisruptionCollectorExecuting<E> New(final ThrowingProcedure<? super E, ?> logic)
	{
		return new DisruptionCollectorExecuting.WrapperThrowingProcedure<>(
			notNull(logic)                                 ,
			DisruptionCollector.defaultCollectionSupplier(),
			null
		);
	}
	
	public static <E> DisruptionCollectorExecuting<E> New(
		final ThrowingProcedure<? super E, ?>            logic             ,
		final Supplier<? extends XCollection<Throwable>> collectionSupplier
	)
	{
		return new DisruptionCollectorExecuting.WrapperThrowingProcedure<>(
			notNull(logic)    ,
			collectionSupplier,
			null
		);
	}
	
	public static <E> DisruptionCollectorExecuting<E> New(
		final ThrowingProcedure<? super E, ?> logic     ,
		final XCollection<Throwable>          collection
	)
	{
		return new DisruptionCollectorExecuting.WrapperThrowingProcedure<>(
			notNull(logic),
			null          ,
			collection
		);
	}
		
	public class WrapperThrowingProcedure<E> extends DisruptionCollector.Default implements DisruptionCollectorExecuting<E>
	{
		private final ThrowingProcedure<? super E, ?> logic;

		public WrapperThrowingProcedure(
			final ThrowingProcedure<? super E, ?>            logic             ,
			final Supplier<? extends XCollection<Throwable>> collectionSupplier,
			final XCollection<Throwable>                     disruptions
		)
		{
			super(collectionSupplier, disruptions);
			this.logic = logic;
		}
		
		@Override
		public void executeOn(final E element)
		{
			this.execute(this.logic, element);
		}
		
		
	}
}
