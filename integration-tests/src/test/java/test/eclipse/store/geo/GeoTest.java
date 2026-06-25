package test.eclipse.store.geo;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.geo.data.Country;
import test.eclipse.store.geo.data.Geo;
import test.eclipse.store.geo.data.generator.Generator;
import test.eclipse.store.geo.data.generator.GeneratorAT;

public class GeoTest
{
	@TempDir
	Path tempDir;

	//@RepeatedTest(10)
	@Test
	void geoDataStoreTest()
	{
		Geo geo = Generator.generateGeo();
		try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(geo, tempDir)) {
		}



		Geo loadedGeo = new Geo();

		Geo finalLoadedGeo = loadedGeo;
		try (EmbeddedStorageManager storageManager =  EmbeddedStorage.start(tempDir)) {
			loadedGeo = (Geo) storageManager.root();
			//System.out.println(loadedGeo.toString());
			for (int i = 0; i < 100; i++) {
				Country austria = new Country("Austria", GeneratorAT.generateATStates());
				loadedGeo.getCountries().add(austria);
				storageManager.store(loadedGeo.getCountries());
			}
		}

//		System.out.println("================= After adding Austria ================");
//		try (EmbeddedStorageManager storageManager =  EmbeddedStorage.start(tempDir)) {
//			loadedGeo = (Geo) storageManager.root();
//			int size = loadedGeo.getCountries().size();
//			System.out.println("XXX: Number of countries first store: " + size);
//			//System.out.println(loadedGeo.toString());
//		}
//
//		try (EmbeddedStorageManager storageManager =  EmbeddedStorage.start(tempDir)) {
//			loadedGeo = (Geo) storageManager.root();
//			List<Country> countries = loadedGeo.getCountries();
//			countries.remove(countries.size() - 1);
//			countries.remove(countries.size() - 1);
//			countries.remove(countries.size() - 1);
//			countries.remove(countries.size() - 1);
//			countries.remove(countries.size() - 1);
//			countries.remove(countries.size() - 1);
//			storageManager.store(loadedGeo.getCountries());
//			int size = loadedGeo.getCountries().size();
//			System.out.println("XXX: Number of countries after remove: " + size);
//
//		}
//
//		try (EmbeddedStorageManager storageManager =  EmbeddedStorage.start(tempDir)) {
//			loadedGeo = (Geo) storageManager.root();
//			//System.out.println(loadedGeo.toString());
////			int size = loadedGeo.getCountries().size();
////			System.out.println("XXX: Number of countries final load: " + size);
//
//		}



		// print all folder and files from tempdir;
//		System.out.println("Files in tempDir:");
//		try {
//			java.nio.file.Files.walk(tempDir)
//				.forEach(path -> System.out.println(path.toString()));
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
	}


}
