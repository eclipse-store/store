package org.eclipse.store.gigamap.indexer.annotation.binary;

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

import org.eclipse.store.gigamap.types.BitmapIndex;
import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class CarBinaryTest
{

    @TempDir
    Path tempDir;

    @Test
    void carUniqueIndex()
    {
        GigaMap<CarBinary> vehicles = GigaMap.New();
        final BitmapIndices<CarBinary> bitmapIndices = vehicles.index().bitmap();
        IndexerGenerator.AnnotationBased(CarBinary.class).generateIndices(bitmapIndices);

        assertThrows(IllegalArgumentException.class, () -> vehicles.add(new CarBinary("notValidVehicle", "Skoda", 0L)));
        vehicles.add(new CarBinary("notValidVehicle", "Skoda", 5555L));

    }

    @Test
    void carBinaryAnnotationTest()
    {
        GigaMap<CarBinary> vehicles = GigaMap.New();
        final BitmapIndices<CarBinary> bitmapIndices = vehicles.index().bitmap();
        IndexerGenerator.AnnotationBased(CarBinary.class).generateIndices(bitmapIndices);

        CarBinary car1 = new CarBinary("1HGCM82633A123456", "Honda", 454545L);
        CarBinary car2 = new CarBinary("1HGCM82633dfsd456", "Toyota", 1L);
        vehicles.addAll(car1, car2);

        BitmapIndex<CarBinary, Long> vehicleId = bitmapIndices.get(Long.class, "vehicleId");

        CarBinary carBinary = vehicles.query(vehicleId.is(1L)).findFirst().orElse(null);
        assertNotNull(carBinary);
        assertEquals("Toyota", carBinary.getMake());

        assertThrows(IllegalArgumentException.class, () -> vehicles.add(new CarBinary("notValidVehicle", "Skoda", 0L)));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(vehicles, tempDir)) {
            vehicles.add(new CarBinary("dkfjsdkljfd", "BMW", 2L));
            vehicles.store();
            // do nothing
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            GigaMap<CarBinary> loadedVehicles = (GigaMap<CarBinary>) storageManager.root();
            BitmapIndex<CarBinary, Long> vehicleId1 = loadedVehicles.index().bitmap().get(Long.class, "vehicleId");
            CarBinary carBinary1 = loadedVehicles.query(vehicleId1.is(1L)).findFirst().orElse(null);
            assertNotNull(carBinary1);
            assertEquals("Toyota", carBinary1.getMake());

        }
    }

    @Test
    void carBinaryAnnotation_updateApiTest()
    {
        GigaMap<CarBinary> vehicles = GigaMap.New();
        final BitmapIndices<CarBinary> bitmapIndices = vehicles.index().bitmap();
        IndexerGenerator.AnnotationBased(CarBinary.class).generateIndices(bitmapIndices);

        CarBinary car1 = new CarBinary("1HGCM82633A123456", "Honda", 454545L);
        CarBinary car2 = new CarBinary("1HGCM82633dfsd456", "Toyota", 1L);
        vehicles.addAll(car1, car2);

        BitmapIndex<CarBinary, Long> vehicleId = bitmapIndices.get(Long.class, "vehicleId");

        CarBinary carBinary = vehicles.query(vehicleId.is(1L)).findFirst().orElse(null);
        assertNotNull(carBinary);
        assertEquals("Toyota", carBinary.getMake());

        assertThrows(IllegalArgumentException.class, () -> vehicles.add(new CarBinary("notValidVehicle", "Skoda", 0L)));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(vehicles, tempDir)) {
            vehicles.add(new CarBinary("dkfjsdkljfd", "BMW", 2L));
            vehicles.store();
            // do nothing
        }

        GigaMap<CarBinary> loadedVehicles = GigaMap.New();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedVehicles, tempDir)) {
            BitmapIndex<CarBinary, Long> vehicleId1 = loadedVehicles.index().bitmap().get(Long.class, "vehicleId");
            CarBinary carBinary1 = loadedVehicles.query(vehicleId1.is(1L)).findFirst().orElse(null);
            assertNotNull(carBinary1);
            assertEquals("Toyota", carBinary1.getMake());

        }
    }
}
