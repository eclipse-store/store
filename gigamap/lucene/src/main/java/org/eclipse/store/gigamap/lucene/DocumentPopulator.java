package org.eclipse.store.gigamap.lucene;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
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

import org.apache.lucene.document.*;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexableField;

/**
 * DocumentPopulator is an abstract class responsible for populating instances of a Lucene {@link Document}
 * with data extracted from a specific entity type.
 * This ensures a consistent structure for indexing and searching operations within a Lucene-based index.
 * <p>
 * Note: This class is intentionally not a functional interface to avoid
 * potential issues with the use of unpersistable lambda instances.
 *
 * @param <E> the type of entity to be used for populating the document
 */
//may NOT be a functional interface to avoid unpersistable lambda instances getting used.
public abstract class DocumentPopulator<E>
{
	/**
	 * Populates the given Lucene {@link Document} with data extracted from the specified entity.
	 * <p>
	 * Implementations of this method should define how to map the data fields of an entity
	 * to the fields in the Lucene document, ensuring consistency for indexing.
	 *
	 * @param document the Lucene {@link Document} to be populated with data
	 * @param entity the source entity whose data will be used to populate the document
	 */
	public abstract void populate(Document document, E entity);
	
	/**
	 * Creates an {@link IndexableField} that stores an integer value in a Lucene document.
	 *
	 * @param name the name of the field to be created
	 * @param value the integer value to be stored in the field
	 * @return an {@link IndexableField} instance representing the integer field
	 */
	public static IndexableField createIntField(final String name, final int value)
	{
		return new IntField(name, value, Store.YES);
	}
	
	/**
	 * Creates an {@link IndexableField} that stores a long value in a Lucene document.
	 *
	 * @param name the name of the field to be created
	 * @param value the long value to be stored in the field
	 * @return an {@link IndexableField} instance representing the long field
	 */
	public static IndexableField createLongField(final String name, final long value)
	{
		return new LongField(name, value, Store.YES);
	}
	
	/**
	 * Creates an {@link IndexableField} that stores a float value in a Lucene document.
	 *
	 * @param name the name of the field to be created
	 * @param value the float value to be stored in the field
	 * @return an {@link IndexableField} instance representing the float field
	 */
	public static IndexableField createFloatField(final String name, final float value)
	{
		return new FloatField(name, value, Store.YES);
	}
	
	/**
	 * Creates an {@link IndexableField} that stores a double value in a Lucene document.
	 *
	 * @param name the name of the field to be created
	 * @param value the double value to be stored in the field
	 * @return an {@link IndexableField} instance representing the double field
	 */
	public static IndexableField createDoubleField(final String name, final double value)
	{
		return new DoubleField(name, value, Store.YES);
	}
	
	/**
	 * Creates an {@link IndexableField} that stores a string value in a Lucene document.
	 *
	 * @param name the name of the field to be created
	 * @param value the string value to be stored in the field
	 * @return an {@link IndexableField} instance representing the string field
	 */
	public static IndexableField createStringField(final String name, final String value)
	{
		return new StringField(name, value, Store.YES);
	}
	
	/**
	 * Creates an {@link IndexableField} that stores a text value in a Lucene document.
	 * This method is intended for fields that require full-text indexing.
	 *
	 * @param name the name of the field to be created
	 * @param value the text value to be stored in the field
	 * @return an {@link IndexableField} instance representing the text field
	 */
	public static IndexableField createTextField(final String name, final String value)
	{
		return new TextField(name, value, Store.YES);
	}
	
	
	

	
//	private IndexableField reCreateField(final IndexableField old)
//	{
//		final StoredValue storedValue = old.storedValue();
//		switch(storedValue.getType())
//		{
//			case INTEGER: return new IntField   (old.name(), storedValue.getIntValue()   , Store.YES);
//			case LONG   : return new LongField  (old.name(), storedValue.getLongValue()  , Store.YES);
//			case FLOAT  : return new FloatField (old.name(), storedValue.getFloatValue() , Store.YES);
//			case DOUBLE : return new DoubleField(old.name(), storedValue.getDoubleValue(), Store.YES);
//			case STRING : return new TextField  (old.name(), storedValue.getStringValue(), Store.YES);
//			default:
//				throw new IllegalArgumentException("Value type not supported: " + storedValue.getType());
//		}
//	}
	
}
