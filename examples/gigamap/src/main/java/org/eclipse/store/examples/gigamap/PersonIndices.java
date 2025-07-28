package org.eclipse.store.examples.gigamap;

/*-
 * #%L
 * EclipseStore Example GigaMap
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

import java.time.LocalDate;

import org.eclipse.store.gigamap.types.BinaryIndexer;
import org.eclipse.store.gigamap.types.IndexerLocalDate;
import org.eclipse.store.gigamap.types.IndexerMultiValue;
import org.eclipse.store.gigamap.types.IndexerString;


/**
 * Static collection of indices for the {@link Person} entity.
 * <p>
 * Either implement Indexer by yourself or extend from one of the abstract inner types, like in this example.
 */
public class PersonIndices
{
	public final static BinaryIndexer<Person> id = new BinaryIndexer.Abstract<>()
	{
		public String name()
		{
			return "id";
		}
		
		@Override
		public long indexBinary(final Person entity)
		{
			return entity.getId();
		}
	};

	public final static IndexerString<Person> firstName = new IndexerString.Abstract<>()
	{
		public String name()
		{
			return "firstName";
		}
		
		@Override
		protected String getString(final Person entity)
		{
			return entity.getFirstName();
		}
	};
	
	public final static IndexerString<Person> lastName = new IndexerString.Abstract<>()
	{
		public String name()
		{
			return "lastName";
		}
		
		@Override
		protected String getString(final Person entity)
		{
			return entity.getLastName();
		}
	};
	
	public final static IndexerLocalDate<Person> dateOfBirth = new IndexerLocalDate.Abstract<>()
	{
		public String name()
		{
			return "dateOfBirth";
		}
		
		@Override
		protected LocalDate getLocalDate(final Person entity)
		{
			return entity.getDateOfBirth();
		}
	};

	public final static IndexerString<Person> city = new IndexerString.Abstract<>()
	{
		public String name()
		{
			return "city";
		}
		
		@Override
		protected String getString(final Person entity)
		{
			return entity.getAddress().getCity();
		}
	};

	public final static IndexerString<Person> country = new IndexerString.Abstract<>()
	{
		public String name()
		{
			return "country";
		}
		
		@Override
		protected String getString(final Person entity)
		{
			return entity.getAddress().getCountry();
		}
	};
	
	public final static IndexerMultiValue<Person, Interest> interests = new IndexerMultiValue.Abstract<>()
	{
		@Override
		public Class<Interest> keyType()
		{
			return Interest.class;
		}

		@Override
		public Iterable<? extends Interest> indexEntityMultiValue(final Person entity)
		{
			return entity.getInterests();
		}
	};
}
