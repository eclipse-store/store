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

import org.eclipse.store.gigamap.jvector.annotations.Vector;
import org.eclipse.store.gigamap.types.GigaIndexAnnotationHandler;
import org.eclipse.store.gigamap.types.GigaIndices;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;

import static org.eclipse.serializer.util.X.notNull;

/**
 * A {@link GigaIndexAnnotationHandler} that contributes a vector (HNSW / approximate nearest
 * neighbor) index for entity types carrying a {@link Vector}-annotated {@code float[]} member.
 * <p>
 * Register it with annotation-based index generation to wire up vector search declaratively:
 * <pre>{@code
 * IndexerGenerator.AnnotationBased(Product.class)
 *     .register(VectorAnnotationHandler.New())
 *     .generateIndices(gigaMap);
 * }</pre>
 * When the entity type carries no {@link Vector} member, {@link #contribute(Class, GigaIndices)} is
 * a no-op. The vector is read from the entity on demand (embedded mode).
 *
 * @param <E> the entity type
 *
 * @see Vector
 */
public final class VectorAnnotationHandler<E> implements GigaIndexAnnotationHandler<E>
{
	/**
	 * Creates a handler for in-memory vector indices.
	 *
	 * @param <E> the entity type
	 * @return a new handler
	 */
	public static <E> VectorAnnotationHandler<E> New()
	{
		return new VectorAnnotationHandler<>(null);
	}

	/**
	 * Creates a handler that stores on-disk vector indices under the given base directory. Each
	 * index is stored in a sub-directory named after the index.
	 *
	 * @param <E>           the entity type
	 * @param indexBaseDir the base directory for on-disk indices
	 * @return a new handler
	 */
	public static <E> VectorAnnotationHandler<E> New(final Path indexBaseDir)
	{
		return new VectorAnnotationHandler<>(notNull(indexBaseDir));
	}


	private final Path indexBaseDir;

	private VectorAnnotationHandler(final Path indexBaseDir)
	{
		super();
		this.indexBaseDir = indexBaseDir;
	}

	@Override
	public void contribute(final Class<E> entityType, final GigaIndices<E> indices)
	{
		final Member member = findVectorMember(entityType);
		if(member == null)
		{
			return;
		}

		final Vector annotation = ((AnnotatedElement)member).getAnnotation(Vector.class);
		final boolean method    = member instanceof Method;
		final String  name      = resolveName(annotation, member, method);

		if(annotation.dimension() <= 0)
		{
			throw new IllegalStateException(
				"@Vector on " + entityType.getTypeName() + "." + member.getName()
				+ " requires a positive dimension"
			);
		}

		final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder()
			.dimension(annotation.dimension())
			.similarityFunction(annotation.similarity())
			.onDisk(annotation.onDisk())
		;
		if(annotation.onDisk())
		{
			if(this.indexBaseDir == null)
			{
				throw new IllegalStateException(
					"@Vector(onDisk = true) requires an index directory; create the handler via "
					+ "VectorAnnotationHandler.New(Path)"
				);
			}
			builder.indexDirectory(this.indexBaseDir.resolve(name));
		}

		final VectorIndices<E> vectorIndices = indices.register(VectorIndices.Category());
		vectorIndices.add(
			name,
			builder.build(),
			new AnnotationVectorizer<>(entityType, member.getName(), method)
		);
	}

	private static Member findVectorMember(final Class<?> entityType)
	{
		Member found = null;
		for(Class<?> c = entityType; c != null && c != Object.class; c = c.getSuperclass())
		{
			for(final Field field : c.getDeclaredFields())
			{
				if(!Modifier.isStatic(field.getModifiers()) && field.isAnnotationPresent(Vector.class))
				{
					validateType(field.getType(), entityType, field.getName());
					found = checkSingle(found, field);
				}
			}
			for(final Method m : c.getDeclaredMethods())
			{
				if(!Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 && m.isAnnotationPresent(Vector.class))
				{
					validateType(m.getReturnType(), entityType, m.getName());
					found = checkSingle(found, m);
				}
			}
		}
		return found;
	}

	private static Member checkSingle(final Member existing, final Member candidate)
	{
		if(existing != null)
		{
			throw new IllegalStateException(
				"Multiple @Vector members found on " + candidate.getDeclaringClass().getTypeName()
				+ "; only one is supported"
			);
		}
		return candidate;
	}

	private static void validateType(final Class<?> type, final Class<?> entityType, final String memberName)
	{
		if(type != float[].class)
		{
			throw new IllegalStateException(
				"@Vector on " + entityType.getTypeName() + "." + memberName
				+ " must be of type float[], but was " + type.getTypeName()
			);
		}
	}

	private static String resolveName(final Vector annotation, final Member member, final boolean method)
	{
		if(annotation.name() != null && !annotation.name().isEmpty())
		{
			return annotation.name();
		}
		final String memberName = member.getName();
		if(method)
		{
			if(memberName.startsWith("get") && memberName.length() > 3)
			{
				return decapitalize(memberName.substring(3));
			}
			if(memberName.startsWith("is") && memberName.length() > 2)
			{
				return decapitalize(memberName.substring(2));
			}
		}
		return memberName;
	}

	private static String decapitalize(final String s)
	{
		if(s.isEmpty())
		{
			return s;
		}
		if(s.length() > 1 && Character.isUpperCase(s.charAt(0)) && Character.isUpperCase(s.charAt(1)))
		{
			return s;
		}
		final char[] chars = s.toCharArray();
		chars[0] = Character.toLowerCase(chars[0]);
		return new String(chars);
	}

}
