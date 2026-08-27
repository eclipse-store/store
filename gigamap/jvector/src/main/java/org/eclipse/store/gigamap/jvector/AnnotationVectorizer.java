package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A {@link Vectorizer} that reads an entity's embedding vector from a {@code float[]} member (field
 * or no-argument getter) discovered via the
 * {@link org.eclipse.store.gigamap.jvector.annotations.Vector} annotation.
 * <p>
 * Operates in embedded mode ({@link #isEmbedded()} returns {@code true}): the vector is read from
 * the entity on demand and not stored a second time by the index. Only the entity type and member
 * identity are held as persistent state; the reflective member is re-resolved lazily so the
 * vectorizer stays cheap to (de)serialize as part of a stored {@code VectorIndex}.
 *
 * @param <E> the entity type
 */
public final class AnnotationVectorizer<E> extends Vectorizer<E>
{
	private final Class<?> declaringClass;
	private final String   memberName;
	private final boolean  method;
	private final boolean  allowNull;

	private transient AccessibleObject member;

	AnnotationVectorizer(
		final Class<?> declaringClass,
		final String   memberName,
		final boolean  method,
		final boolean  allowNull
	)
	{
		super();
		this.declaringClass = declaringClass;
		this.memberName     = memberName;
		this.method         = method;
		this.allowNull      = allowNull;
	}

	private AccessibleObject member()
	{
		if(this.member == null)
		{
			try
			{
				this.member = this.method
					? this.declaringClass.getDeclaredMethod(this.memberName)
					: this.declaringClass.getDeclaredField(this.memberName)
				;
				this.member.trySetAccessible();
			}
			catch(final NoSuchMethodException | NoSuchFieldException e)
			{
				throw new RuntimeException(e);
			}
		}
		return this.member;
	}

	@Override
	public float[] vectorize(final E entity)
	{
		try
		{
			final AccessibleObject m = this.member();
			return this.method
				? (float[])((Method)m).invoke(entity)
				: (float[])((Field)m).get(entity)
			;
		}
		catch(final ReflectiveOperationException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isEmbedded()
	{
		return true;
	}

	@Override
	public boolean allowsNullVectors()
	{
		return this.allowNull;
	}
}
