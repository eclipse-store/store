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

/**
 * Exception thrown when a unique constraint violation occurs.
 * This exception is typically encountered when trying to insert or update
 * an entity in a datastore or database where a uniqueness constraint on a specific
 * field or fields is violated by the operation.
 */
public class UniqueConstraintViolationException extends ConstraintViolationException
{
	/**
	 * Constructs a new UniqueConstraintViolationException with the specified entity identifier,
	 * replaced entity, and violating entity.
	 *
	 * @param entityId the identifier of the entity associated with this exception.
	 *                 This typically represents the entity involved in the unique constraint violation.
	 * @param replacedEntity the entity that has been replaced or is being replaced in the
	 *                       context of the unique constraint violation.
	 * @param violatingEntity the entity that violated the uniqueness constraint, causing
	 *                        this exception to be thrown.
	 */
	public UniqueConstraintViolationException(
		final long   entityId       ,
		final Object replacedEntity ,
		final Object violatingEntity
	)
	{
		super(entityId, replacedEntity, violatingEntity);
	}
	
	/**
	 * Constructs a new UniqueConstraintViolationException with the specified entity identifier,
	 * replaced entity, violating entity, detail message, and cause.
	 *
	 * @param entityId the identifier of the entity associated with this exception.
	 *                 It typically represents the entity involved in the unique constraint violation.
	 * @param replacedEntity the entity that has been replaced or is being replaced in the context
	 *                       of the unique constraint violation.
	 * @param violatingEntity the entity that violated the uniqueness constraint, causing this exception
	 *                        to be thrown.
	 * @param message the detail message, which provides more information about the exception.
	 * @param cause the cause of the exception, which can be retrieved later using the
	 *              {@code Throwable.getCause()} method. A null value indicates that the cause
	 *              is non-existent or unknown.
	 */
	public UniqueConstraintViolationException(
		final long      entityId       ,
		final Object    replacedEntity ,
		final Object    violatingEntity,
		final String    message        ,
		final Throwable cause
	)
	{
		super(entityId, replacedEntity, violatingEntity, message, cause);
	}
	
	/**
	 * Constructs a new UniqueConstraintViolationException with the specified entity identifier,
	 * replaced entity, violating entity, and detail message.
	 *
	 * @param entityId the identifier of the entity associated with this exception.
	 *                 This typically represents the entity involved in the unique constraint violation.
	 * @param replacedEntity the entity that has been replaced or is being replaced in the
	 *                       context of the unique constraint violation.
	 * @param violatingEntity the entity that violated the uniqueness constraint, causing
	 *                       */
	public UniqueConstraintViolationException(
		final long   entityId       ,
		final Object replacedEntity ,
		final Object violatingEntity,
		final String message
	)
	{
		super(entityId, replacedEntity, violatingEntity, message);
	}
	
	/**
	 * Constructs a new UniqueConstraintViolationException with the specified entity identifier,
	 * replaced entity, violating entity, and cause.
	 *
	 * @param entityId the identifier of the entity associated with this exception.
	 *                 This typically represents the entity involved in the unique constraint violation.
	 * @param replacedEntity the entity that has been replaced or is being replaced in the
	 *                       context of the unique constraint violation.
	 * @param violatingEntity the entity that violated the uniqueness constraint, causing
	 *                        this exception to be thrown.
	 * @param cause the cause of the exception, which can be retrieved later using the
	 *              {@code Throwable.getCause()} method. A null value indicates that the cause
	 *              is non-existent or unknown.
	 */
	public UniqueConstraintViolationException(
		final long      entityId       ,
		final Object    replacedEntity ,
		final Object    violatingEntity,
		final Throwable cause
	)
	{
		super(entityId, replacedEntity, violatingEntity, cause);
	}
	
	/**
	 * Constructs a new UniqueConstraintViolationException with the specified entity identifier,
	 * replaced entity, violating entity, detail message, cause, suppression enabled/disabled,
	 * and writable stack trace enabled/disabled.
	 *
	 * @param entityId the identifier of the entity associated with this exception.
	 *                 This typically represents the entity involved in the unique constraint violation.
	 * @param replacedEntity the entity that has been replaced or is being replaced in the
	 *                       context of the unique constraint violation.
	 * @param violatingEntity the entity that violated the uniqueness constraint, causing
	 *                        this exception to be thrown.
	 * @param message the detail message, which provides more information about the exception.
	 * @param cause the cause of the exception, which can be retrieved later using the
	 *              {@code Throwable.getCause()} method. A null value indicates that the cause
	 *              is non-existent or unknown.
	 * @param enableSuppression whether or not suppression is enabled or disabled.
	 * @param writableStackTrace whether or not the stack trace should be writable.
	 */
	public UniqueConstraintViolationException(
		final long      entityId          ,
		final Object    replacedEntity    ,
		final Object    violatingEntity   ,
		final String    message           ,
		final Throwable cause             ,
		final boolean   enableSuppression ,
		final boolean   writableStackTrace
	)
	{
		super(entityId, replacedEntity, violatingEntity, message, cause, enableSuppression, writableStackTrace);
	}
	
}
