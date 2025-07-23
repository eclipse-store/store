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

import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.exceptions.BaseException;


/**
 * Represents an exception that occurs when a constraint violation is encountered
 * during the execution of an operation. This typically indicates that certain
 * conditions or constraints on an entity or operation have been violated.
 * <p>
 * Instances of this exception provide details about the entity involved
 * in the violation, including its identifier, the entity being replaced,
 * and the entity causing the violation.
 * <p>
 * This exception extends {@link RuntimeException}, allowing it to be
 * thrown without requiring explicit handling.
 */
public class ConstraintViolationException extends BaseException
{
	private final long   entityId       ;
	private final Object replacedEntity ;
	private final Object violatingEntity;
	
	/**
	 * Constructs a new ConstraintViolationException with the specified entity identifier,
	 * replaced entity, and violating entity.
	 *
	 * @param entityId the identifier of the entity associated with this exception.
	 *                 It may represent the entity involved in a conflict or violation.
	 * @param replacedEntity the entity that has been replaced or is being replaced in the
	 *                       context of the constraint violation.
	 * @param violatingEntity the entity that violated the defined constraint, causing
	 *                        this exception to be raised.
	 */
	public ConstraintViolationException(
		final long   entityId       ,
		final Object replacedEntity ,
		final Object violatingEntity
	)
	{
		super();
		this.entityId        = entityId       ;
		this.replacedEntity  = replacedEntity ;
		this.violatingEntity = violatingEntity;
	}
	
	/**
	 * Constructs a new ConstraintViolationException with the specified entity identifier,
	 * replaced entity, violating entity, detail message, and cause.
	 *
	 * @param entityId the identifier of the entity associated with this exception.
	 *                 It may represent the entity involved in a conflict or violation.
	 * @param replacedEntity the entity that has been replaced or is being replaced in the
	 *                       context of the constraint violation.
	 * @param violatingEntity the entity that violated the defined constraint, causing
	 *                        this exception to be raised.
	 * @param message the detail message, which provides more information about the exception.
	 * @param cause the cause of the exception, which can be retrieved later using the
	 *              {@code Throwable.getCause()} method. A null value indicates that
	 *              the cause is non-existent or unknown.
	 */
	public ConstraintViolationException(
		final long      entityId       ,
		final Object    replacedEntity ,
		final Object    violatingEntity,
		final String    message        ,
		final Throwable cause
	)
	{
		super(message, cause);
		this.entityId        = entityId       ;
		this.replacedEntity  = replacedEntity ;
		this.violatingEntity = violatingEntity;
	}
	
	/**
	 * Constructs a new ConstraintViolationException with the specified entity identifier,
	 * replaced entity, violating entity, and detail message.
	 *
	 * @param entityId the identifier of the entity associated with this exception.
	 *                 It may represent the entity involved in a conflict or violation.
	 * @param replacedEntity the entity that has been replaced or is being replaced in the
	 *                       context of the constraint violation.
	 * @param violatingEntity the entity that violated the defined constraint, causing
	 *                        this exception to be raised.
	 * @param message the detail message, which provides more information about the exception.
	 */
	public ConstraintViolationException(
		final long      entityId       ,
		final Object    replacedEntity ,
		final Object    violatingEntity,
		final String    message
	)
	{
		super(message);
		this.entityId        = entityId       ;
		this.replacedEntity  = replacedEntity ;
		this.violatingEntity = violatingEntity;
	}
	
	/**
	 * Constructs a new ConstraintViolationException with the specified entity identifier,
	 * replaced entity, violating entity, and cause.
	 *
	 * @param entityId the identifier of the entity associated with this exception.
	 *                 It may represent the entity involved in a conflict or violation.
	 * @param replacedEntity the entity that has been replaced or is being replaced in the
	 *                       context of the constraint violation.
	 * @param violatingEntity the entity that violated the defined constraint, causing
	 *                        this exception to be raised.
	 * @param cause the cause of the exception, which can be retrieved later using the
	 *              {@code Throwable.getCause()} method. A null value indicates that
	 *              the cause is non-existent or unknown.
	 */
	public ConstraintViolationException(
		final long      entityId       ,
		final Object    replacedEntity ,
		final Object    violatingEntity,
		final Throwable cause
	)
	{
		super(cause);
		this.entityId        = entityId       ;
		this.replacedEntity  = replacedEntity ;
		this.violatingEntity = violatingEntity;
	}
	
	/**
	 * Constructs a new ConstraintViolationException with the specified entity identifier,
	 * replaced entity, violating entity, detail message, cause, suppression enabled/disabled,
	 * and writable stack trace enabled/disabled.
	 *
	 * @param entityId the identifier of the entity associated with this exception.
	 *                 It may represent the entity involved in a conflict or violation.
	 * @param replacedEntity the entity that has been replaced or is being replaced in the
	 *                       context of the constraint violation.
	 * @param violatingEntity the entity that violated the defined constraint, causing
	 *                        this exception to be raised.
	 * @param message the detail message, which provides more information about the exception.
	 * @param cause the cause of the exception, which can be retrieved later using the
	 *              {@code Throwable.getCause()} method. A null value indicates that
	 *              the cause is non-existent or unknown.
	 * @param enableSuppression whether or not suppression is enabled or disabled.
	 * @param writableStackTrace whether or not the stack trace should be writable.
	 */
	public ConstraintViolationException(
		final long      entityId          ,
		final Object    replacedEntity    ,
		final Object    violatingEntity   ,
		final String    message           ,
		final Throwable cause             ,
		final boolean   enableSuppression ,
		final boolean   writableStackTrace
	)
	{
		super(message, cause, enableSuppression, writableStackTrace);
		this.entityId        = entityId       ;
		this.replacedEntity  = replacedEntity ;
		this.violatingEntity = violatingEntity;
	}
	
	/**
	 * Returns the entity that violated the constraint, which might cause a failure
	 * during the execution of a certain operation.
	 *
	 * @return the object representing the violating entity, or null if no entity is defined.
	 */
	public Object getViolatingEntity()
	{
		return this.violatingEntity;
	}
	
	/**
	 * Retrieves the entity that has been replaced in the context of this exception.
	 *
	 * @return the object representing the replaced entity, or null if no entity is defined.
	 */
	public Object getReplacedEntity()
	{
		return this.replacedEntity;
	}
	
	/**
	 * Retrieves the identifier of the entity associated with this exception.
	 *
	 * @return -1L for violations occurring during adding, positive otherwise.
	 */
	public long getEntityId()
	{
		return this.entityId;
	}
	
	@Override
	public String assembleDetailString()
	{
		return VarString.New()
			.add("entityId=").add(this.entityId)
			.add(", replacedEntity=").add(this.replacedEntity)
			.add(", violatingEntity=").add(this.violatingEntity)
			.toString()
		;
	}
	
}
