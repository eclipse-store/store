package org.eclipse.store.gigamap.indexer;

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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerMultiValue;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultiIndexerTest
{
	@Test
	void queryMultiIndex()
	{
		final Insurance i1 = new Insurance(1, "I1");
		final Insurance i2 = new Insurance(2, "I2");
		final Insurance i3 = new Insurance(3, "I3");
		final Insurance i4 = new Insurance(4, "I4");
				
		final GigaMap<Patient> gigaMap = GigaMap.<Patient>Builder()
			.withBitmapIndex(insurancesIndex)
			.build();
		gigaMap.add(new Patient("P1", i1));
		gigaMap.add(new Patient("P2", i2));
		gigaMap.add(new Patient("P3", i3));
		gigaMap.add(new Patient("P4", i1, i2, i3));
		gigaMap.add(new Patient("P5", i1, i2));
		gigaMap.add(new Patient("P6", i2, i3));
		
		assertEquals(
			3,
			gigaMap.query(insurancesIndex.is(i1)).count()
		);
		
		assertEquals(
			4,
			gigaMap.query(insurancesIndex.is(i2)).count()
		);
		
		assertEquals(
			2,
			gigaMap.query(insurancesIndex.all(i1, i2)).count()
		);

		assertEquals(
			1,
			gigaMap.query(insurancesIndex.all(i1, i2, i3)).count()
		);
		
		assertEquals(
			5,
			gigaMap.query(insurancesIndex.in(i1, i3)).count()
		);
		
		assertEquals(
			0,
			gigaMap.query(insurancesIndex.is(i4)).count()
		);
		
		assertEquals(
			0,
			gigaMap.query(insurancesIndex.all(i1, i2, i3, i4)).count()
		);
		
		assertEquals(
			3,
			gigaMap.query(insurancesIndex.not(i1)).count()
		);
		
		assertEquals(
			1,
			gigaMap.query(insurancesIndex.notIn(i1, i2)).count()
		);
		
		assertEquals(
			"P4",
			gigaMap.query(insurancesIndex.all(i1, i2, i3)).toList().get(0).name
		);
		
		assertEquals(
			4,
			gigaMap.query(insurancesIndex.is(i -> i.id % 2 == 0)).count()
		);
		
	}
		
	
	static IndexerMultiValue<Patient, Insurance> insurancesIndex = new IndexerMultiValue.Abstract<>()
	{
		@Override
		public Iterable<Insurance> indexEntityMultiValue(final Patient entity)
		{
			return entity.insurances;
		}

		@Override
		public Class<Insurance> keyType()
		{
			return Insurance.class;
		}
		
	};
	
	
	
	static class Patient
	{
		final String name;
		final List<Insurance> insurances;
		
		Patient(final String name, final Insurance... insurances)
		{
			super();
			this.name       = name;
			this.insurances = Arrays.asList(insurances);
		}

		@Override
		public String toString()
		{
			return "Patient [name=" + this.name + ", insurances=" + this.insurances + "]";
		}
		
	}
	
	
	static class Insurance
	{
		final int id;
		final String name;
		
		Insurance(final int id, final String name)
		{
			super();
			this.id   = id;
			this.name = name;
		}

		@Override
		public String toString()
		{
			return "Insurance [id=" + this.id + ", name=" + this.name + "]";
		}
		
	}
	
}
