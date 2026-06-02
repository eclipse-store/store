package org.eclipse.store.gigamap.lucene;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.eclipse.store.gigamap.lucene.annotations.FullText;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link DocumentPopulator} that maps the {@link FullText}-annotated members (fields or
 * no-argument getters) of an entity type into Lucene document fields.
 * <p>
 * Only the entity type is held as persistent state; the annotated members are re-discovered
 * reflectively (and lazily) so the populator stays cheap to (de)serialize as part of a stored
 * {@code LuceneIndex}.
 *
 * @param <E> the entity type
 */
public final class AnnotationDocumentPopulator<E> extends DocumentPopulator<E>
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final Class<E> entityType;

	private transient List<TextMember> members;


	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	AnnotationDocumentPopulator(final Class<E> entityType)
	{
		super();
		this.entityType = entityType;
	}


	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	/**
	 * Returns whether the given type carries at least one {@link FullText} annotated member.
	 *
	 * @param entityType the type to inspect
	 * @return true if a full-text member is present
	 */
	static boolean hasFullTextMembers(final Class<?> entityType)
	{
		return !collect(entityType).isEmpty();
	}

	private static List<TextMember> collect(final Class<?> entityType)
	{
		final List<TextMember> result    = new ArrayList<>();
		final Set<String>      seenProps = new HashSet<>();

		for(Class<?> c = entityType; c != null && c != Object.class; c = c.getSuperclass())
		{
			for(final Field field : c.getDeclaredFields())
			{
				if(Modifier.isStatic(field.getModifiers()))
				{
					continue;
				}
				final FullText annotation = field.getAnnotation(FullText.class);
				if(annotation != null && seenProps.add(field.getName()))
				{
					result.add(new TextMember(field, fieldName(annotation, field.getName(), null), annotation));
				}
			}
		}

		for(Class<?> c = entityType; c != null && c != Object.class; c = c.getSuperclass())
		{
			for(final Method method : c.getDeclaredMethods())
			{
				if(Modifier.isStatic(method.getModifiers())
					|| method.getParameterCount() != 0
					|| method.getReturnType() == void.class)
				{
					continue;
				}
				final FullText annotation = method.getAnnotation(FullText.class);
				if(annotation == null)
				{
					continue;
				}
				// a property annotated on both the field and its accessor (e.g. record components) must
				// only be indexed once; the field takes precedence, matching the bitmap generator.
				final String property = propertyName(method.getName(), method.getReturnType());
				if(seenProps.add(property))
				{
					result.add(new TextMember(
						method,
						fieldName(annotation, method.getName(), method.getReturnType()),
						annotation
					));
				}
			}
		}

		return result;
	}

	private static String propertyName(final String methodName, final Class<?> returnType)
	{
		if(methodName.startsWith("get") && methodName.length() > 3)
		{
			return decapitalize(methodName.substring(3));
		}
		if(methodName.startsWith("is") && methodName.length() > 2
			&& (returnType == boolean.class || returnType == Boolean.class))
		{
			return decapitalize(methodName.substring(2));
		}
		return methodName;
	}

	private List<TextMember> members()
	{
		if(this.members == null)
		{
			this.members = collect(this.entityType);
		}
		return this.members;
	}

	@Override
	public void populate(final Document document, final E entity)
	{
		for(final TextMember member : this.members())
		{
			final Object value = member.read(entity);
			if(value == null)
			{
				continue;
			}
			final String text  = String.valueOf(value);
			final Store  store = member.store ? Store.YES : Store.NO;
			document.add(member.analyzed
				? new TextField(member.fieldName, text, store)
				: new StringField(member.fieldName, text, store)
			);
		}
	}

	private static String fieldName(final FullText annotation, final String memberName, final Class<?> returnType)
	{
		if(annotation.name() != null && !annotation.name().isEmpty())
		{
			return annotation.name();
		}
		// fields: member name as-is; methods: strip get/is prefix
		if(returnType == null)
		{
			return memberName;
		}
		if(memberName.startsWith("get") && memberName.length() > 3)
		{
			return decapitalize(memberName.substring(3));
		}
		if(memberName.startsWith("is") && memberName.length() > 2
			&& (returnType == boolean.class || returnType == Boolean.class))
		{
			return decapitalize(memberName.substring(2));
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


	private static final class TextMember
	{
		final AccessibleObject member;
		final boolean          method;
		final String           fieldName;
		final boolean          analyzed;
		final boolean          store;

		TextMember(final Member member, final String fieldName, final FullText annotation)
		{
			this.member    = (AccessibleObject)member;
			this.method    = member instanceof Method;
			this.fieldName = fieldName;
			this.analyzed  = annotation.analyzed();
			this.store     = annotation.store();
			this.member.trySetAccessible();
		}

		Object read(final Object entity)
		{
			try
			{
				return this.method
					? ((Method)this.member).invoke(entity)
					: ((Field)this.member).get(entity)
				;
			}
			catch(final ReflectiveOperationException e)
			{
				throw new RuntimeException(e);
			}
		}
	}

}
