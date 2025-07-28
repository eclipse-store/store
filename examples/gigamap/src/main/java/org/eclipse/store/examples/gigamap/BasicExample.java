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

import com.github.javafaker.Faker;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.GigaQuery;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.nio.file.Paths;
import java.time.Year;
import java.util.List;

import static org.eclipse.store.examples.gigamap.PersonIndices.*;


public class BasicExample
{
	final static EmbeddedStorageManager storageManager = startStorage();
	final static GigaMap<Person>        gigaMap        = ensureGigaMap();

	static EmbeddedStorageManager startStorage()
	{
		return EmbeddedStorage.start(Paths.get("target/basic-example"));
	}
	
	@SuppressWarnings("unchecked")
	static GigaMap<Person> ensureGigaMap()
	{
		GigaMap<Person> gigaMap = (GigaMap<Person>)storageManager.root();
		if(gigaMap == null)
		{
			System.out.print("Creating random data ... ");
			
			storageManager.setRoot(gigaMap = RandomGenerator.createMap(1_000_000));
			storageManager.storeRoot();
			
			System.out.println("finished");
			printSpacer();
		}
		return gigaMap;
	}
	
	
	public static void main(final String[] args)
	{
		queryExamples();
		
//		updateExample();
		
		storageManager.shutdown();
	}
	
	static void queryExamples()
	{
		queryExample1();
		printSpacer();
		queryExample2();
		printSpacer();
		queryExample3();
		printSpacer();
		queryExample4();
		printSpacer();
		queryExample5();
		printSpacer();
	}
	
	
	static void queryExample1()
	{
		query(
			"Thomases",
			gigaMap.query(firstName.is("Thomas"))
		);
	}
	
	static void queryExample2()
	{
		final int birthYear = Year.now().getValue() - 25;
		query(
			"25 year olds",
			gigaMap.query(dateOfBirth.isYear(birthYear))
		);
	}
	
	static void queryExample3()
	{
		query(
			"Germans and Austrians",
			gigaMap.query(country.in("Germany", "Austria"))
		);
	}
	
	static void queryExample4()
	{
		query(
			"%sch% in last name",
			gigaMap.query(lastName.contains("sch"))
		);
	}
	
	static void queryExample5()
	{
		query(
			"sport as interest",
			gigaMap.query(interests.is(Interest.SPORTS))
		);
	}
	
	static void updateExample()
	{
		final Person person = gigaMap.query(id.is(1L)).findFirst().orElseThrow();
		System.out.println("Before: " + person);
		
		// updates person and indices
		gigaMap.update(person, p -> p.setAddress(new Address(new Faker())));
		gigaMap.store();
		
		System.out.println("After: " + person);
	}
	
	
	static void query(
		final String title,
		final GigaQuery<Person> query
	)
	{
		System.out.println(title);
		final long start = System.nanoTime();
		final List<Person> result = query.toList(10);
		System.out.println((System.nanoTime() - start) / 1_000_000.0 + " ms");
		result.forEach(System.out::println);
		System.out.println("...");
	}
	
	static void printSpacer()
	{
		System.out.println();
		System.out.println("========================================================");
		System.out.println();
	}
	
}
