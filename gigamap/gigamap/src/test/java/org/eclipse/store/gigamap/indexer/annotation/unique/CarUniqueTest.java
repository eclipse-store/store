package org.eclipse.store.gigamap.indexer.annotation.unique;

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

import org.eclipse.store.gigamap.exceptions.UniqueConstraintViolationExceptionBitmap;
import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

public class CarUniqueTest
{
    @TempDir
    Path tempDir;

    @Test
    public void carUniqueTest()
    {
        GigaMap<Car> vehicles = GigaMap.New();
        final BitmapIndices<Car> bitmapIndices = vehicles.index().bitmap();
        IndexerGenerator.AnnotationBased(Car.class).generateIndices(bitmapIndices);

        // Create a new cars with the same VIN
        Car car1 = new Car("1HGCM82633A123456", "Honda");
        Car car2 = new Car("1HGCM82633A123456", "Toyota");

        vehicles.add(car1);
        UniqueConstraintViolationExceptionBitmap ex = Assertions.assertThrows(UniqueConstraintViolationExceptionBitmap.class, () -> vehicles.add(car2));
        Assertions.assertTrue(ex.getMessage().contains("1HGCM82633A123456"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(vehicles, tempDir)) {
        }

        // Load the vehicles from storage and check if the unique constraint is still enforced
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            GigaMap<Car> loadedVehicles = (GigaMap<Car>) storageManager.root();
            UniqueConstraintViolationExceptionBitmap ex1 =
                    Assertions.assertThrows(UniqueConstraintViolationExceptionBitmap.class,
                            () -> loadedVehicles.add(car2)); //<-- No exception
            Assertions.assertTrue(ex1.getMessage().contains("1HGCM82633A123456"));
        }
    }


    @Test
    public void carUnique_updateApiTest()
    {
        GigaMap<Car> vehicles = GigaMap.New();
        final BitmapIndices<Car> bitmapIndices = vehicles.index().bitmap();
        IndexerGenerator.AnnotationBased(Car.class).generateIndices(bitmapIndices);

        // Create a new cars with the same VIN
        Car car1 = new Car("1HGCM82633A123456", "Honda");
        Car car2 = new Car("1HGCM82633A123456", "Toyota");

        vehicles.add(car1);
        UniqueConstraintViolationExceptionBitmap ex = Assertions.assertThrows(UniqueConstraintViolationExceptionBitmap.class, () -> vehicles.add(car2));
        Assertions.assertTrue(ex.getMessage().contains("1HGCM82633A123456"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(vehicles, tempDir)) {
        }

        // Load the vehicles from storage and check if the unique constraint is still enforced
        GigaMap<Car> loadedVehicles = GigaMap.New();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedVehicles, tempDir)) {
            UniqueConstraintViolationExceptionBitmap ex1 =
                    Assertions.assertThrows(UniqueConstraintViolationExceptionBitmap.class,
                            () -> loadedVehicles.add(car2)); //<-- No exception
            Assertions.assertTrue(ex1.getMessage().contains("1HGCM82633A123456"));
        }
    }
}
