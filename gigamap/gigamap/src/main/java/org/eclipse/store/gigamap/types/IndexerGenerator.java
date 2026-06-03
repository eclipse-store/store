package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
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

import org.eclipse.store.gigamap.annotations.Identity;
import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.store.gigamap.annotations.IndexKind;
import org.eclipse.store.gigamap.annotations.SpatialIndex;
import org.eclipse.store.gigamap.annotations.Unique;
import org.eclipse.store.gigamap.types.Indexer.Creator;
import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.EqHashEnum;
import org.eclipse.serializer.collections.HashEnum;
import org.eclipse.serializer.collections.types.XEnum;
import org.eclipse.serializer.collections.types.XList;
import org.eclipse.serializer.exceptions.IllegalAccessRuntimeException;
import org.eclipse.serializer.exceptions.NoSuchFieldRuntimeException;
import org.eclipse.serializer.reflect.XReflect;
import org.eclipse.serializer.typing.XTypes;

import java.lang.reflect.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.eclipse.serializer.util.X.notNull;


/**
 * The IndexerGenerator class is responsible for generating indices for managing
 * and organizing data in a structured format. This class provides utilities to
 * create indices based on annotations or other mechanisms. It serves as an integral
 * component in indexing operations for objects of a specified type.
 * <p>
 * Annotation-based generation natively produces {@link BitmapIndex bitmap indices} from
 * {@link Index}, {@link Unique}, {@link Identity} and {@link SpatialIndex} annotations. Index types
 * provided by integration modules (for example full-text or vector search) can participate by
 * registering a {@link GigaIndexAnnotationHandler} via {@link #register(GigaIndexAnnotationHandler)}
 * and invoking {@link #generateIndices(GigaMap)}.
 *
 * @param <E> the type parameter representing the type of the entities that will
 *            be indexed.
 */
public interface IndexerGenerator<E>
{
	/**
	 * Fills the provided BitmapIndices object with index data.
	 *
	 * @param target the BitmapIndices object to be populated with generated index data
	 */
	public void generateIndices(BitmapIndices<E> target);

	/**
	 * Generates the annotation-based indices for the given {@link GigaMap}.
	 * <p>
	 * This default implementation generates the bitmap indices only (equivalent to
	 * {@link #generateIndices(BitmapIndices)} on {@code target.index().bitmap()}). The
	 * {@link AnnotationBased} implementation additionally invokes the registered
	 * {@link GigaIndexAnnotationHandler handlers} to contribute further index groups (for example
	 * full-text or vector indices).
	 *
	 * @param target the {@link GigaMap} whose indices are to be generated
	 */
	public default void generateIndices(final GigaMap<E> target)
	{
		this.generateIndices(target.index().bitmap());
	}

	/**
	 * Registers a {@link GigaIndexAnnotationHandler} that contributes additional index groups during
	 * {@link #generateIndices(GigaMap)}. Handlers are invoked in registration order, after the bitmap
	 * indices have been generated.
	 *
	 * @param handler the handler to register
	 * @return this generator, for fluent chaining
	 */
	public default IndexerGenerator<E> register(final GigaIndexAnnotationHandler<E> handler)
	{
		throw new UnsupportedOperationException();
	}


	/**
	 * Creates and returns an instance of IndexerGenerator using an annotation-based configuration
	 * for the provided entity type.
	 *
	 * @param entityType the class type of the entity for which the IndexerGenerator will be created.
	 *                   Must not be null.
	 * @return an instance of IndexerGenerator configured for the given entity type.
	 */
	public static <E> IndexerGenerator<E> AnnotationBased(final Class<E> entityType)
	{
		return new AnnotationBased<>(
			notNull(entityType)
		);
	}

	/**
	 * Convenience entry point that generates all annotation-based indices for the given
	 * {@link GigaMap} using the supplied handlers.
	 *
	 * @param entityType the annotated entity type
	 * @param map        the target {@link GigaMap}
	 * @param handlers   the {@link GigaIndexAnnotationHandler handlers} to apply (may be empty)
	 */
	@SafeVarargs
	public static <E> void generate(
		final Class<E>                       entityType,
		final GigaMap<E>                     map       ,
		final GigaIndexAnnotationHandler<E>... handlers
	)
	{
		final IndexerGenerator<E> generator = AnnotationBased(entityType);
		for(final GigaIndexAnnotationHandler<E> handler : handlers)
		{
			generator.register(handler);
		}
		generator.generateIndices(map);
	}


	public static class AnnotationBased<E> implements IndexerGenerator<E>
	{
		private final Class<E>                              entityType;
		private final List<GigaIndexAnnotationHandler<E>>   handlers = new ArrayList<>();

		AnnotationBased(final Class<E> entityType)
		{
			super();
			this.entityType = entityType;
		}

		@Override
		public IndexerGenerator<E> register(final GigaIndexAnnotationHandler<E> handler)
		{
			this.handlers.add(notNull(handler));
			return this;
		}

		@Override
		public void generateIndices(final GigaMap<E> target)
		{
			final GigaIndices<E> indices = target.index();
			this.generateIndices(indices.bitmap());
			for(final GigaIndexAnnotationHandler<E> handler : this.handlers)
			{
				handler.contribute(this.entityType, indices);
			}
		}

		@Override
		public void generateIndices(final BitmapIndices<E> target)
		{
			final XEnum<String>        indexNames      = EqHashEnum.New();
			final XList<Indexer<E, ?>> uniqueIndexers  = BulkList.New();
			final XList<Indexer<E, ?>> indexers        = BulkList.New();
			final XEnum<Indexer<E, ?>> identityIndices = HashEnum.New();

			for(final MemberAccessor accessor : this.collectAnnotatedMembers())
			{
				final AnnotatedElement annotated = accessor.annotated();
				final Index    index    = annotated.getAnnotation(Index.class);
				final Unique   unique   = annotated.getAnnotation(Unique.class);
				final Identity identity = annotated.getAnnotation(Identity.class);

				// @Index is optional: a member carrying @Unique or @Identity alone is indexed too.
				if(index == null && unique == null && identity == null)
				{
					continue;
				}

				final String indexName = this.getIndexName(index, accessor);
				if(!indexNames.add(indexName))
				{
					throw new IllegalStateException("Double index name '" + indexName + "' in " + this.entityType.getCanonicalName());
				}

				final Indexer<E, ?> indexer = this.createIndexer(index, accessor, unique != null);

				// a member with both @Index and @Unique is registered only as a unique constraint
				if(unique != null)
				{
					uniqueIndexers.add(indexer);
				}
				else
				{
					indexers.add(indexer);
				}
				if(identity != null)
				{
					identityIndices.add(indexer);
				}
			}

			this.addSpatialIndices(indexers, indexNames);

			// Idempotent: skip unique constraints already registered and ensure (rather than add) the
			// remaining indices, so the generator can run more than once on the same GigaMap without
			// failing on already-present index names.
			final XEnum<String> existingUnique = HashEnum.New();
			target.accessUniqueConstraints(constraints ->
			{
				for(final GigaIndex<E> constraint : constraints)
				{
					existingUnique.add(constraint.name());
				}
			});
			final XList<Indexer<E, ?>> newUniqueIndexers = BulkList.New();
			for(final Indexer<E, ?> uniqueIndexer : uniqueIndexers)
			{
				if(!existingUnique.contains(uniqueIndexer.name()))
				{
					newUniqueIndexers.add(uniqueIndexer);
				}
			}
			if(!newUniqueIndexers.isEmpty())
			{
				target.addUniqueConstraints(newUniqueIndexers);
			}

			target.ensureAll(indexers);
			target.setIdentityIndices(identityIndices);
		}

		private List<MemberAccessor> collectAnnotatedMembers()
		{
			final List<MemberAccessor> result    = new ArrayList<>();
			final Set<String>          seenProps = new HashSet<>();

			for(final Field field : XReflect.collectInstanceFields(
				this.entityType,
				field -> !Modifier.isStatic(field.getModifiers())
			))
			{
				if(isIndexAnnotated(field))
				{
					final MemberAccessor accessor = MemberAccessor.forField(field);
					result.add(accessor);
					seenProps.add(normalizeProperty(accessor.propertyName()));
				}
			}

			for(Class<?> c = this.entityType; c != null && c != Object.class; c = c.getSuperclass())
			{
				for(final Method method : c.getDeclaredMethods())
				{
					if(Modifier.isStatic(method.getModifiers())
						|| method.getParameterCount() != 0
						|| method.getReturnType() == void.class)
					{
						continue;
					}
					if(isIndexAnnotated(method))
					{
						final MemberAccessor accessor = MemberAccessor.forMethod(method);
						// a property annotated on both the field and its accessor must only yield a single
						// index; the field (collected above) takes precedence. Compared case-insensitively
						// so an acronym getter (getURL) matches its field (url).
						if(seenProps.add(normalizeProperty(accessor.propertyName())))
						{
							result.add(accessor);
						}
					}
				}
			}

			return result;
		}

		private static boolean isIndexAnnotated(final AnnotatedElement element)
		{
			return element.isAnnotationPresent(Index.class)
				|| element.isAnnotationPresent(Unique.class)
				|| element.isAnnotationPresent(Identity.class)
			;
		}

		private static String normalizeProperty(final String propertyName)
		{
			return propertyName.toLowerCase(Locale.ROOT);
		}

		private void addSpatialIndices(final XList<Indexer<E, ?>> indexers, final XEnum<String> indexNames)
		{
			final SpatialIndex spatial = this.entityType.getAnnotation(SpatialIndex.class);
			if(spatial == null)
			{
				return;
			}
			final String name = XChars.isEmpty(spatial.name()) ? "spatial" : spatial.name();
			if(!indexNames.add(name))
			{
				throw new IllegalStateException("Double index name '" + name + "' in " + this.entityType.getCanonicalName());
			}
			indexers.add(new SpatialIndexerField<>(
				name,
				this.resolveMember(spatial.latitude()),
				this.resolveMember(spatial.longitude())
			));
		}

		private MemberAccessor resolveMember(final String propertyName)
		{
			for(final Field field : XReflect.collectInstanceFields(
				this.entityType,
				field -> !Modifier.isStatic(field.getModifiers())
			))
			{
				if(field.getName().equals(propertyName))
				{
					return MemberAccessor.forField(field);
				}
			}

			for(Class<?> c = this.entityType; c != null && c != Object.class; c = c.getSuperclass())
			{
				for(final Method method : c.getDeclaredMethods())
				{
					if(Modifier.isStatic(method.getModifiers())
						|| method.getParameterCount() != 0
						|| method.getReturnType() == void.class)
					{
						continue;
					}
					if(method.getName().equals(propertyName)
						|| MemberAccessor.derivePropertyName(method.getName(), method.getReturnType()).equals(propertyName))
					{
						return MemberAccessor.forMethod(method);
					}
				}
			}

			throw new IllegalStateException(
				"No field or getter '" + propertyName + "' found in " + this.entityType.getCanonicalName()
			);
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		private Indexer<E, ?> createIndexer(final Index annotation, final MemberAccessor accessor, final boolean unique)
		{
			final Class<?> type = accessor.type();
			final String   name = this.getIndexName(annotation, accessor);

			if(annotation != null)
			{
				final Class<? extends Creator> creatorClass = annotation.creator();
				if(!Creator.Dummy.class.equals(creatorClass))
				{
					return this.instantiateCreator(creatorClass, name, accessor);
				}
			}

			final IndexKind kind           = annotation != null ? annotation.kind() : IndexKind.AUTO;
			final boolean   explicitBinary = kind == IndexKind.BINARY || (annotation != null && annotation.binary());
			final boolean   preferBinary   = explicitBinary || (kind == IndexKind.AUTO && unique);

			if(kind == IndexKind.BIT_SLICED)
			{
				final Indexer<E, ?> bitSliced = this.createBitSliced(type, name, accessor);
				if(bitSliced != null)
				{
					return bitSliced;
				}
				throw new IllegalStateException(
					"Unsupported field type for annotation based bit-sliced index generation: " + type.getTypeName()
				);
			}

			if(preferBinary)
			{
				if(XTypes.isNaturalNumberType(type))
				{
					return new BinaryIndexerField(name, accessor);
				}
				if(String.class.equals(type))
				{
					return new BinaryIndexerStringField(name, accessor);
				}
				if(UUID.class.equals(type))
				{
					return new BinaryIndexerUUIDField(name, accessor);
				}
				if(explicitBinary)
				{
					throw new IllegalStateException(
						"Unsupported field type for annotation based binary index generation: " + type.getTypeName()
					);
				}
				// unique on a non-binary type: fall through to a standard indexer (used as unique constraint)
			}

			if(String.class.equals(type))
			{
				return new IndexerStringField(name, accessor);
			}
			if(XTypes.isCharacterType(type))
			{
				return new IndexerCharacterField(name, accessor);
			}
			if(XTypes.isIntegerType(type))
			{
				return new IndexerIntegerField(name, accessor);
			}
			if(XTypes.isLongType(type))
			{
				return new IndexerLongField(name, accessor);
			}
			if(XTypes.isFloatType(type))
			{
				return new IndexerFloatField(name, accessor);
			}
			if(XTypes.isDoubleType(type))
			{
				return new IndexerDoubleField(name, accessor);
			}
			if(XTypes.isByteType(type))
			{
				return new IndexerByteField(name, accessor);
			}
			if(XTypes.isShortType(type))
			{
				return new IndexerShortField(name, accessor);
			}
			if(XTypes.isBooleanType(type))
			{
				return new IndexerBooleanField(name, accessor);
			}
			if(LocalDate.class.equals(type))
			{
				return new IndexerLocalDateField(name, accessor);
			}
			if(LocalTime.class.equals(type))
			{
				return new IndexerLocalTimeField(name, accessor);
			}
			if(LocalDateTime.class.equals(type))
			{
				return new IndexerLocalDateTimeField(name, accessor);
			}
			if(YearMonth.class.equals(type))
			{
				return new IndexerYearMonthField(name, accessor);
			}
			if(Instant.class.equals(type))
			{
				return new IndexerInstantField(name, accessor);
			}
			if(ZonedDateTime.class.equals(type))
			{
				return new IndexerZonedDateTimeField(name, accessor);
			}
			if(UUID.class.equals(type))
			{
				return new BinaryIndexerUUIDField(name, accessor);
			}
			if(type.isEnum())
			{
				return new IndexerEnumField(name, accessor, type);
			}
			if(Iterable.class.isAssignableFrom(type))
			{
				final Type genericType = accessor.genericType();
				if(IndexerMultiValueFieldIterable.isValidType(genericType))
				{
					return new IndexerMultiValueFieldIterable(name, accessor);
				}

				throw new IllegalStateException(
					"Unsupported field type for annotation based multi value index generation: " + genericType.getTypeName()
				);
			}
			if(type.isArray())
			{
				return new IndexerMultiValueFieldArray(name, accessor);
			}

			return new IndexerCustomField<>(name, accessor);
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		private Indexer<E, ?> createBitSliced(final Class<?> type, final String name, final MemberAccessor accessor)
		{
			if(XTypes.isIntegerType(type))
			{
				return new ByteIndexerIntegerField(name, accessor);
			}
			if(XTypes.isLongType(type))
			{
				return new ByteIndexerLongField(name, accessor);
			}
			if(XTypes.isByteType(type))
			{
				return new ByteIndexerByteField(name, accessor);
			}
			if(XTypes.isShortType(type))
			{
				return new ByteIndexerShortField(name, accessor);
			}
			if(XTypes.isFloatType(type))
			{
				return new ByteIndexerFloatField(name, accessor);
			}
			if(XTypes.isDoubleType(type))
			{
				return new ByteIndexerDoubleField(name, accessor);
			}
			return null;
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		private Indexer<E, ?> instantiateCreator(
			final Class<? extends Creator> creatorClass,
			final String                   name        ,
			final MemberAccessor           accessor
		)
		{
			// Instantiate from this (the GigaMap) module rather than via the serializer module, so the
			// same "opens <entity-pkg> to org.eclipse.store.gigamap" that already enables member access
			// also covers the creator.
			final Creator creator;
			try
			{
				final Constructor<? extends Creator> constructor = creatorClass.getDeclaredConstructor();
				constructor.trySetAccessible();
				creator = constructor.newInstance();
			}
			catch(final ReflectiveOperationException e)
			{
				throw new RuntimeException(
					"Could not instantiate index creator " + creatorClass.getName()
					+ " (an accessible no-argument constructor is required)", e
				);
			}
			if(creator instanceof Creator.MemberAware)
			{
				((Creator.MemberAware)creator).initialize(name, accessor.reflectMember());
			}
			return (Indexer<E, ?>)creator.create();
		}

		private String getIndexName(final Index annotation, final MemberAccessor accessor)
		{
			final String annotationName = annotation != null ? annotation.name() : null;
			return !XChars.isEmpty(annotationName)
				? annotationName
				: accessor.propertyName()
			;
		}


		/**
		 * Reflective accessor for an annotated member, which is either a {@link Field} or a
		 * no-argument getter {@link Method}. The underlying member is held {@code transient} and
		 * re-resolved on demand so generated indexers stay (de)serializable.
		 */
		static final class MemberAccessor
		{
			private final Class<?>            declaringClass;
			private final String              memberName;
			private final boolean             method;
			private transient AccessibleObject member;

			static MemberAccessor forField(final Field field)
			{
				return new MemberAccessor(field.getDeclaringClass(), field.getName(), false, field);
			}

			static MemberAccessor forMethod(final Method method)
			{
				return new MemberAccessor(method.getDeclaringClass(), method.getName(), true, method);
			}

			private MemberAccessor(
				final Class<?>         declaringClass,
				final String           memberName    ,
				final boolean          method        ,
				final AccessibleObject member
			)
			{
				this.declaringClass = declaringClass;
				this.memberName     = memberName;
				this.method         = method;
				this.member         = member;
				if(member != null)
				{
					member.trySetAccessible();
				}
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
					catch(final NoSuchMethodException e)
					{
						throw new RuntimeException(e);
					}
					catch(final NoSuchFieldException e)
					{
						throw new NoSuchFieldRuntimeException(e);
					}
				}
				return this.member;
			}

			Member reflectMember()
			{
				return (Member)this.member();
			}

			AnnotatedElement annotated()
			{
				return this.member();
			}

			Class<?> type()
			{
				final AccessibleObject m = this.member();
				return this.method ? ((Method)m).getReturnType() : ((Field)m).getType();
			}

			Type genericType()
			{
				final AccessibleObject m = this.member();
				return this.method ? ((Method)m).getGenericReturnType() : ((Field)m).getGenericType();
			}

			Class<?> componentType()
			{
				return this.type().getComponentType();
			}

			String propertyName()
			{
				return this.method
					? derivePropertyName(this.memberName, this.type())
					: this.memberName
				;
			}

			@SuppressWarnings("unchecked")
			<T> T getValue(final Object entity)
			{
				final AccessibleObject m = this.member();
				try
				{
					return this.method
						? (T)((Method)m).invoke(entity)
						: (T)((Field)m).get(entity)
					;
				}
				catch(final IllegalAccessException e)
				{
					throw new IllegalAccessRuntimeException(e);
				}
				catch(final InvocationTargetException e)
				{
					throw new RuntimeException(e);
				}
			}

			static String derivePropertyName(final String methodName, final Class<?> returnType)
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

			private static String decapitalize(final String s)
			{
				if(s.isEmpty())
				{
					return s;
				}
				// JavaBeans rule: leave names that start with two upper-case letters unchanged (e.g. "URL")
				if(s.length() > 1 && Character.isUpperCase(s.charAt(0)) && Character.isUpperCase(s.charAt(1)))
				{
					return s;
				}
				final char[] chars = s.toCharArray();
				chars[0] = Character.toLowerCase(chars[0]);
				return new String(chars);
			}
		}


		static class BinaryIndexerField<E> extends BinaryIndexer.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			BinaryIndexerField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			public long indexBinary(final E entity)
			{
				final Number key = this.accessor.getValue(entity);
				if(key == null)
				{
					throw new IllegalArgumentException("Null keys are not allowed in index " + this.indexName);
				}
				return key.longValue();
			}

		}


		static class IndexerStringField<E> extends IndexerString.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerStringField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected String getString(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class BinaryIndexerStringField<E> extends BinaryIndexerString.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			BinaryIndexerStringField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected String getString(final E entity)
			{
				return this.accessor.getValue(entity);
			}
		}


		static class IndexerCharacterField<E> extends IndexerCharacter.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerCharacterField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Character getCharacter(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerIntegerField<E> extends IndexerInteger.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerIntegerField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Integer getInteger(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerLongField<E> extends IndexerLong.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerLongField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Long getLong(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerFloatField<E> extends IndexerFloat.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerFloatField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Float getFloat(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerDoubleField<E> extends IndexerDouble.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerDoubleField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Double getDouble(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerByteField<E> extends IndexerByte.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerByteField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Byte getByte(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerShortField<E> extends IndexerShort.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerShortField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Short getShort(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerBooleanField<E> extends IndexerBoolean.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerBooleanField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Boolean getBoolean(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerLocalDateField<E> extends IndexerLocalDate.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerLocalDateField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected LocalDate getLocalDate(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerLocalTimeField<E> extends IndexerLocalTime.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerLocalTimeField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected LocalTime getLocalTime(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerLocalDateTimeField<E> extends IndexerLocalDateTime.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerLocalDateTimeField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected LocalDateTime getLocalDateTime(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerYearMonthField<E> extends IndexerYearMonth.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerYearMonthField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected YearMonth getYearMonth(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerInstantField<E> extends IndexerInstant.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerInstantField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Instant getInstant(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerZonedDateTimeField<E> extends IndexerZonedDateTime.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerZonedDateTimeField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected ZonedDateTime getZonedDateTime(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class BinaryIndexerUUIDField<E> extends BinaryIndexerUUID.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			BinaryIndexerUUIDField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected UUID getUUID(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class ByteIndexerIntegerField<E> extends ByteIndexerInteger.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			ByteIndexerIntegerField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Integer getInteger(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class ByteIndexerLongField<E> extends ByteIndexerLong.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			ByteIndexerLongField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Long getLong(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class ByteIndexerByteField<E> extends ByteIndexerByte.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			ByteIndexerByteField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Byte getByte(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class ByteIndexerShortField<E> extends ByteIndexerShort.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			ByteIndexerShortField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Short getShort(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class ByteIndexerFloatField<E> extends ByteIndexerFloat.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			ByteIndexerFloatField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Float getFloat(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class ByteIndexerDoubleField<E> extends ByteIndexerDouble.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			ByteIndexerDoubleField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Double getDouble(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerEnumField<E, K extends Enum<K>> extends Indexer.Abstract<E, K>
		{
			private final String         indexName;
			private final MemberAccessor accessor;
			private final Class<K>       keyType;

			IndexerEnumField(final String indexName, final MemberAccessor accessor, final Class<K> keyType)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
				this.keyType   = keyType;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			public Class<K> keyType()
			{
				return this.keyType;
			}

			@Override
			public K index(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class SpatialIndexerField<E> extends SpatialIndexer.Abstract<E>
		{
			private final String         indexName;
			private final MemberAccessor latitude;
			private final MemberAccessor longitude;

			SpatialIndexerField(final String indexName, final MemberAccessor latitude, final MemberAccessor longitude)
			{
				this.indexName = indexName;
				this.latitude  = latitude;
				this.longitude = longitude;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@Override
			protected Double getLatitude(final E entity)
			{
				return this.coordinate(this.latitude.getValue(entity), "latitude");
			}

			@Override
			protected Double getLongitude(final E entity)
			{
				return this.coordinate(this.longitude.getValue(entity), "longitude");
			}

			private Double coordinate(final Object value, final String axis)
			{
				if(value == null)
				{
					return null;
				}
				if(value instanceof Number)
				{
					return ((Number)value).doubleValue();
				}
				throw new IllegalStateException(
					"Spatial index '" + this.indexName + "' " + axis
					+ " member must be numeric, but was " + value.getClass().getTypeName()
				);
			}

		}


		static class IndexerMultiValueFieldIterable<E, K> extends IndexerMultiValue.Abstract<E, K>
		{
			static boolean isValidType(final Type type)
			{
				if(type instanceof ParameterizedType)
				{
					final Type[] args = ((ParameterizedType)type).getActualTypeArguments();
					return args.length == 1 && args[0] instanceof Class;
				}

				return false;
			}

			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerMultiValueFieldIterable(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@SuppressWarnings("unchecked")
			@Override
			public Class<K> keyType()
			{
				final Type genericType = this.accessor.genericType();
				return (Class<K>)((ParameterizedType)genericType).getActualTypeArguments()[0];
			}

			@Override
			public Iterable<? extends K> indexEntityMultiValue(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}


		static class IndexerMultiValueFieldArray<E, K> extends IndexerMultiValue.Abstract<E, K>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerMultiValueFieldArray(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@SuppressWarnings("unchecked")
			@Override
			public Class<K> keyType()
			{
				return (Class<K>)this.accessor.componentType();
			}

			@SuppressWarnings("unchecked")
			@Override
			public Iterable<? extends K> indexEntityMultiValue(final E entity)
			{
				final Object array = this.accessor.getValue(entity);
				if(array == null)
				{
					return null;
				}
				final int     length = Array.getLength(array);
				final List<K> list   = new ArrayList<>(length);
				for(int i = 0; i < length; i++)
				{
					list.add((K)Array.get(array, i));
				}
				return list;
			}

		}


		static class IndexerCustomField<E, K> extends Indexer.Abstract<E, K>
		{
			private final String         indexName;
			private final MemberAccessor accessor;

			IndexerCustomField(final String indexName, final MemberAccessor accessor)
			{
				this.indexName = indexName;
				this.accessor  = accessor;
			}

			@Override
			public String name()
			{
				return this.indexName;
			}

			@SuppressWarnings("unchecked")
			@Override
			public Class<K> keyType()
			{
				return (Class<K>)this.accessor.type();
			}

			@Override
			public K index(final E entity)
			{
				return this.accessor.getValue(entity);
			}

		}

	}

}
