package org.eclipse.store.gigamap.exceptions;

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

import org.eclipse.store.gigamap.types.BitmapIndex;
import org.eclipse.serializer.chars.VarString;


/**
 * Exception thrown when a unique constraint violation is encountered specifically in the context
 * of a {@link BitmapIndex}. This exception is typically used to indicate that an operation has
 * resulted in a conflict with a uniqueness constraint enforced by the specified {@code BitmapIndex}.
 */
public class UniqueConstraintViolationExceptionBitmap extends UniqueConstraintViolationException
{
	private final BitmapIndex<?, ?> violatedIndex;
	
	/**
	 * Constructs a new instance of the {@code UniqueConstraintViolationExceptionBitmap} class.
	 * This exception is thrown when an operation violates a unique constraint
	 * in the context of a {@link BitmapIndex}.
	 *
	 * @param entityId         The unique identifier of the entity involved in the violation.
	 * @param replacedEntity   The entity being replaced as a result of the violation.
	 * @param violatingEntity  The entity that caused the unique constraint violation.
	 * @param violatedIndex    The {@link BitmapIndex} that enforces the unique constraint
	 *                         which was violated.
	 */
	public UniqueConstraintViolationExceptionBitmap(
		final long              entityId       ,
		final Object            replacedEntity ,
		final Object            violatingEntity,
		final BitmapIndex<?, ?> violatedIndex
	)
	{
		super(entityId, replacedEntity, violatingEntity);
		this.violatedIndex = violatedIndex;
	}
	
	/**
	 * Constructs a new instance of the {@code UniqueConstraintViolationExceptionBitmap} class.
	 * This exception is thrown when an operation violates a unique constraint
	 * in the context of a {@link BitmapIndex}.
	 *
	 * @param entityId         The unique identifier of the entity involved in the violation.
	 * @param replacedEntity   The entity being replaced as a result of the violation.
	 * @param violatingEntity  The entity that caused the unique constraint violation.
	 * @param violatedIndex    The {@link BitmapIndex} that enforces the unique constraint
	 *                         which was violated.
	 * @param message          A detailed message describing the violation.
	 * @param cause            The underlying cause of the exception, if available.
	 */
	public UniqueConstraintViolationExceptionBitmap(
		final long              entityId       ,
		final Object            replacedEntity ,
		final Object            violatingEntity,
		final BitmapIndex<?, ?> violatedIndex  ,
		final String            message        ,
		final Throwable         cause
	)
	{
		super(entityId, replacedEntity, violatingEntity, message, cause);
		this.violatedIndex = violatedIndex;
	}
	
	/**
	 * Constructs a new instance of the {@code UniqueConstraintViolationExceptionBitmap} class.
	 * This exception is thrown when an operation violates a unique constraint
	 * in the context of a {@link BitmapIndex}.
	 *
	 * @param entityId         The unique identifier of the entity involved in the violation.
	 * @param replacedEntity   The entity being replaced as a result of the violation.
	 * @param violatingEntity  The entity that caused the unique constraint violation.
	 * @param violatedIndex    The {@link BitmapIndex} that enforces the unique constraint
	 *                         which was violated.
	 * @param message          A detailed message describing the violation.
	 */
	public UniqueConstraintViolationExceptionBitmap(
		final long              entityId       ,
		final Object            replacedEntity ,
		final Object            violatingEntity,
		final BitmapIndex<?, ?> violatedIndex  ,
		final String            message
	)
	{
		super(entityId, replacedEntity, violatingEntity, message);
		this.violatedIndex = violatedIndex;
	}
	
	/**
	 * Constructs a new instance of the {@code UniqueConstraintViolationExceptionBitmap} class.
	 * This exception is thrown when an operation violates a unique constraint
	 * in the context of a {@link BitmapIndex}.
	 *
	 * @param entityId         The unique identifier of the entity involved in the violation.
	 * @param replacedEntity   The entity being replaced as a result of the violation.
	 * @param violatingEntity  The entity that caused the unique constraint violation.
	 * @param violatedIndex    The {@link BitmapIndex} that enforces the unique constraint
	 *                         which was violated.
	 * @param cause            The underlying cause of the exception, if available.
	 */
	public UniqueConstraintViolationExceptionBitmap(
		final long              entityId       ,
		final Object            replacedEntity ,
		final Object            violatingEntity,
		final BitmapIndex<?, ?> violatedIndex  ,
		final Throwable         cause
	)
	{
		super(entityId, replacedEntity, violatingEntity, cause);
		this.violatedIndex = violatedIndex;
	}
	
	/**
	 * Constructs a new instance of the {@code UniqueConstraintViolationExceptionBitmap} class.
	 * This exception is thrown when an operation violates a unique constraint
	 * in the context of a {@link BitmapIndex}.
	 *
	 * @param entityId          The unique identifier of the entity involved in the violation.
	 * @param replacedEntity    The entity being replaced as a result of the violation.
	 * @param violatingEntity   The entity that caused the unique constraint violation.
	 * @param violatedIndex     The {@link BitmapIndex} that enforces the unique constraint which was violated.
	 * @param message           A detailed message describing the violation.
	 * @param cause             The underlying cause of the exception, if available.
	 * @param enableSuppression Whether suppression is enabled or disabled.
	 * @param writableStackTrace Whether the stack trace should be writable.
	 */
	public UniqueConstraintViolationExceptionBitmap(
		final long              entityId          ,
		final Object            replacedEntity    ,
		final Object            violatingEntity   ,
		final BitmapIndex<?, ?> violatedIndex     ,
		final String            message           ,
		final Throwable         cause             ,
		final boolean           enableSuppression ,
		final boolean           writableStackTrace
	)
	{
		super(entityId, replacedEntity, violatingEntity, message, cause, enableSuppression, writableStackTrace);
		this.violatedIndex = violatedIndex;
	}
	
	/**
	 * Retrieves the {@link BitmapIndex} that enforces the unique constraint which was violated.
	 *
	 * @return The {@link BitmapIndex} object associated with the unique constraint violation.
	 */
	public BitmapIndex<?, ?> getViolatedIndex()
	{
		return this.violatedIndex;
	}
	
	@Override
	public String assembleDetailString()
	{
		return VarString.New()
			.add("violatedIndex=").add(this.violatedIndex.name())
			.add(", ")
			.add(super.assembleDetailString())
			.toString()
		;
	}
	
}
