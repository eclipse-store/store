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

import org.eclipse.store.gigamap.annotations.Identity;
import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.store.gigamap.annotations.Unique;
import org.eclipse.store.gigamap.types.Indexer.Creator;
import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.EqHashEnum;
import org.eclipse.serializer.collections.HashEnum;
import org.eclipse.serializer.collections.HashTable;
import org.eclipse.serializer.collections.types.XEnum;
import org.eclipse.serializer.collections.types.XList;
import org.eclipse.serializer.collections.types.XTable;
import org.eclipse.serializer.exceptions.IllegalAccessRuntimeException;
import org.eclipse.serializer.exceptions.NoSuchFieldRuntimeException;
import org.eclipse.serializer.reflect.XReflect;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.serializer.typing.XTypes;

import java.lang.reflect.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.eclipse.serializer.util.X.notNull;


/**
 * The IndexerGenerator class is responsible for generating indices for managing
 * and organizing data in a structured format. This class provides utilities to
 * create indices based on annotations or other mechanisms. It serves as an integral
 * component in indexing operations for objects of a specified type.
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
	
	
	public static class AnnotationBased<E> implements IndexerGenerator<E>
	{
		private final Class<E> entityType;

		AnnotationBased(final Class<E> entityType)
		{
			super();
			this.entityType = entityType;
		}
	
		@Override
		public void generateIndices(final BitmapIndices<E> target)
		{
			final XTable<Field, Index>    indexTable    = HashTable.New();
			final XTable<Field, Unique>   uniqueTable   = HashTable.New();
			final XTable<Field, Identity> identityTable = HashTable.New();
			final XEnum<String>           indexNames    = EqHashEnum.New();

			final Field[] fields = XReflect.collectInstanceFields(
				this.entityType,
				field -> !Modifier.isStatic(field.getModifiers())
			);
			for(final Field field : fields)
			{
				final Index index = field.getAnnotation(Index.class);
				if(index != null)
				{
					final String indexName = this.getIndexName(index, field);
					if(!indexNames.add(indexName))
					{
						throw new IllegalStateException("Double index name '" + indexName + "' in " + this.entityType.getCanonicalName());
					}
					indexTable.add(field, index);
					
					final Unique unique = field.getAnnotation(Unique.class);
					if(unique != null)
					{
						uniqueTable.add(field, unique);
					}
					
					final Identity identity = field.getAnnotation(Identity.class);
					if(identity != null)
					{
						identityTable.add(field, identity);
					}
				}
				
			}

			final XList<Indexer<E, ?>> uniqueIndexers  = BulkList.New();
			final XList<Indexer<E, ?>> indexers        = BulkList.New();
			final XEnum<Indexer<E, ?>> identityIndices = HashEnum.New();
			
			for(final Field field : uniqueTable.keys())
			{
				final Indexer<E, ?> indexer = this.createIndexer(
					indexTable.removeFor(field),
					field,
					true
				);
				if(identityTable.get(field) != null)
				{
					identityIndices.add(indexer);
				}
				uniqueIndexers.add(indexer);
			}

			for(final KeyValue<Field, Index> kv : indexTable)
			{
				final Field field      = kv.key();
				final Index annotation = kv.value();
				
				final Indexer<E, ?> indexer = this.createIndexer(annotation, field, false);
				if(identityTable.get(field) != null)
				{
					identityIndices.add(indexer);
				}
				indexers.add(indexer);
			}

			target.addUniqueConstraints(uniqueIndexers);
			target.addAll(indexers);
			target.setIdentityIndices(identityIndices);
		}
		
		@SuppressWarnings({"rawtypes", "unchecked"})
		private Indexer<E, ?> createIndexer(final Index annotation, final Field field, final boolean unique)
		{
			boolean forceBinary = false;
			if(annotation != null)
			{
				final Class<? extends Creator> creatorClass = annotation.creator();
				if(!Creator.Dummy.class.equals(creatorClass))
				{
					return (Indexer<E, ?>)XReflect.defaultInstantiate(creatorClass);
				}
				
				forceBinary = annotation.binary();
			}
			
			final Class<?> type = field.getType();
			final String   name = this.getIndexName(annotation, field);
			
			if(unique || forceBinary)
			{
				if(XTypes.isNaturalNumberType(type))
				{
					return new BinaryIndexerField(name, field);
				}
				if(String.class.equals(type))
				{
					return new BinaryIndexerStringField(name, field);
				}
				if(forceBinary)
				{
					throw new IllegalStateException(
						"Unsupported field type for annotation based binary index generation: " + type.getTypeName()
					);
				}
			}
			
			if(String.class.equals(type))
			{
				return new IndexerStringField(name, field);
			}
			if(XTypes.isCharacterType(type))
			{
				return new IndexerCharacterField(name, field);
			}
			if(XTypes.isIntegerType(type))
			{
				return new IndexerIntegerField(name, field);
			}
			if(XTypes.isLongType(type))
			{
				return new IndexerLongField(name, field);
			}
			if(XTypes.isFloatType(type))
			{
				return new IndexerFloatField(name, field);
			}
			if(XTypes.isDoubleType(type))
			{
				return new IndexerDoubleField(name, field);
			}
			if(XTypes.isByteType(type))
			{
				return new IndexerByteField(name, field);
			}
			if(XTypes.isShortType(type))
			{
				return new IndexerShortField(name, field);
			}
			if(XTypes.isBooleanType(type))
			{
				return new IndexerBooleanField(name, field);
			}
			if(LocalDate.class.equals(type))
			{
				return new IndexerLocalDateField(name, field);
			}
			if(LocalTime.class.equals(type))
			{
				return new IndexerLocalTimeField(name, field);
			}
			if(LocalDateTime.class.equals(type))
			{
				return new IndexerLocalDateTimeField(name, field);
			}
			if(YearMonth.class.equals(type))
			{
				return new IndexerYearMonthField(name, field);
			}
			if(UUID.class.equals(type))
			{
				return new BinaryIndexerUUIDField(name, field);
			}
			if(Iterable.class.isAssignableFrom(type))
			{
				final Type genericType = field.getGenericType();
				if(IndexerMultiValueFieldIterable.isValidType(genericType))
				{
					return new IndexerMultiValueFieldIterable(name, field);
				}
				
				throw new IllegalStateException(
					"Unsupported field type for annotation based multi value index generation: " + genericType.getTypeName()
				);
			}
			if(type.isArray())
			{
				return new IndexerMultiValueFieldArray(name, field);
			}
			
			return new IndexerCustomField<>(name, field);
		}
		
		private String getIndexName(final Index annotation, final Field field)
		{
			String annotationName = null;
			if(annotation != null)
			{
				annotationName = annotation.name();
			}
			return !XChars.isEmpty(annotationName)
				? annotationName
				: field.getName()
			;
		}
				
		
		static class FieldInfo
		{
			private final Class<?>  clazz;
			private final String    fieldName;
			private transient Field field;
			
			FieldInfo(final Field field)
			{
				this.clazz     = field.getDeclaringClass();
				this.fieldName = field.getName();
				this.field     = field;
			}
			
			Field field()
			{
				if(this.field == null)
				{
					try
					{
						this.field = this.clazz.getDeclaredField(this.fieldName);
					}
					catch(final NoSuchFieldException e)
					{
						throw new NoSuchFieldRuntimeException(e);
					}
				}
				return this.field;
			}
			
			@SuppressWarnings("unchecked")
			<T> T getValue(final Object entity)
			{
				try
				{
					return (T)this.field().get(entity);
				}
				catch(final IllegalAccessException e)
				{
					throw new IllegalAccessRuntimeException(e);
				}
			}
		}
		
		
		static class BinaryIndexerField<E> extends BinaryIndexer.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			BinaryIndexerField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
			
			@Override
			public long indexBinary(final E entity)
			{
				final Number key = this.fieldInfo.getValue(entity);
				return key.longValue();
			}
			
		}
		
		
		static class IndexerStringField<E> extends IndexerString.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerStringField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
					
			@Override
			protected String getString(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
		
		static class BinaryIndexerStringField<E> extends BinaryIndexerString.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			BinaryIndexerStringField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
			
			@Override
			protected String getString(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
		}
		
		
		static class IndexerCharacterField<E> extends IndexerCharacter.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerCharacterField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
					
			@Override
			protected Character getCharacter(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
		
		static class IndexerIntegerField<E> extends IndexerInteger.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerIntegerField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
					
			@Override
			protected Integer getInteger(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
		
		static class IndexerLongField<E> extends IndexerLong.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerLongField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
					
			@Override
			protected Long getLong(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
		
		static class IndexerFloatField<E> extends IndexerFloat.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerFloatField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
					
			@Override
			protected Float getFloat(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
		
		static class IndexerDoubleField<E> extends IndexerDouble.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerDoubleField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
					
			@Override
			protected Double getDouble(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
		
		static class IndexerByteField<E> extends IndexerByte.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerByteField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
					
			@Override
			protected Byte getByte(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
		
		static class IndexerShortField<E> extends IndexerShort.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerShortField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
					
			@Override
			protected Short getShort(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
		
		static class IndexerBooleanField<E> extends IndexerBoolean.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerBooleanField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
					
			@Override
			protected Boolean getBoolean(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
		
		static class IndexerLocalDateField<E> extends IndexerLocalDate.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerLocalDateField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
			
			@Override
			protected LocalDate getLocalDate(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
		
		static class IndexerLocalTimeField<E> extends IndexerLocalTime.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerLocalTimeField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
			
			@Override
			protected LocalTime getLocalTime(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
		
		static class IndexerLocalDateTimeField<E> extends IndexerLocalDateTime.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerLocalDateTimeField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
			
			@Override
			protected LocalDateTime getLocalDateTime(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
		
		static class IndexerYearMonthField<E> extends IndexerYearMonth.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerYearMonthField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
			
			@Override
			protected YearMonth getYearMonth(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
		
		static class BinaryIndexerUUIDField<E> extends BinaryIndexerUUID.Abstract<E>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			BinaryIndexerUUIDField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
			}
			
			@Override
			public String name()
			{
				return this.indexName;
			}
			
			@Override
			protected UUID getUUID(final E entity)
			{
				return this.fieldInfo.getValue(entity);
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
			
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerMultiValueFieldIterable(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
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
				final Type genericType = this.fieldInfo.field().getGenericType();
				return (Class<K>)((ParameterizedType)genericType).getActualTypeArguments()[0];
			}
			
			@Override
			public Iterable<? extends K> indexEntityMultiValue(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
		
		static class IndexerMultiValueFieldArray<E, K> extends IndexerMultiValue.Abstract<E, K>
		{
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerMultiValueFieldArray(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
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
				return (Class<K>)this.fieldInfo.clazz.getComponentType();
			}
			
			@SuppressWarnings("unchecked")
			@Override
			public Iterable<? extends K> indexEntityMultiValue(final E entity)
			{
				final Object array = this.fieldInfo.getValue(entity);
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
			private final String    indexName;
			private final FieldInfo fieldInfo;
			
			IndexerCustomField(final String indexName, final Field field)
			{
				this.indexName = indexName;
				this.fieldInfo = new FieldInfo(field);
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
				return (Class<K>)this.fieldInfo.field().getType();
			}
			
			@Override
			public K index(final E entity)
			{
				return this.fieldInfo.getValue(entity);
			}
			
		}
		
	}
	
}
