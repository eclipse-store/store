package org.eclipse.store.gigamap.restart;

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
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.RepeatedTest;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class AllTypesUpdateTest
{
    private static final int MAX_SIZE = 200;
    
    
    @RepeatedTest(3)
    void updateString()
    {
        Path dataDirectory = Path.of("target", "updateString");
        runWithOperation(dataDirectory, p -> p.setStringField("Hello World"));
    }
    
    @RepeatedTest(3)
    void updateIntField()
    {
        Path dataDirectory = Path.of("target", "updateIntField");
        runWithOperation(dataDirectory, p -> p.setIntField(42));
    }
    
    @RepeatedTest(3)
    void updateLongField()
    {
        Path dataDirectory = Path.of("target", "updateLongField");
        runWithOperation(dataDirectory, p -> p.setLongField(45L));
    }
    
    @RepeatedTest(3)
    void updateDoubleField()
    {
        Path dataDirectory = Path.of("target", "updateDoubleField");
        runWithOperation(dataDirectory, p -> p.setDoubleField(42.0));
    }
    
    @RepeatedTest(3)
    void updateBooleanField() {
        Path dataDirectory = Path.of("target", "updateBooleanField");
        runWithOperation(dataDirectory, p -> p.setBooleanField(false));
        
    }
    
    @RepeatedTest(3)
    void updateCharField() {
        Path dataDirectory = Path.of("target", "updateCharField");
        runWithOperation(dataDirectory, p -> p.setCharField('a'));
    }
    
    @RepeatedTest(3)
    void updateByteField()
    {
        Path dataDirectory = Path.of("target", "updateByteField");
        runWithOperation(dataDirectory, p -> p.setByteField((byte) 42));
    }
    
    @RepeatedTest(3)
    void updateShortField()
    {
        Path dataDirectory = Path.of("target", "updateShortField");
        runWithOperation(dataDirectory, p -> p.setShortField((short) 42));
    }
    
    @RepeatedTest(3)
    void updateFloatField()
    {
        Path dataDirectory = Path.of("target", "updateFloatField");
        runWithOperation(dataDirectory, p -> p.setFloatField(42.0f));
    }
    
    @RepeatedTest(3)
    void updateIntegerField() {
        Path dataDirectory = Path.of("target", "updateIntegerField");
        runWithOperation(dataDirectory, p -> p.setIntegerField(42));
    }
    
    @RepeatedTest(3)
    void updateLongObjectField() {
        Path dataDirectory = Path.of("target", "updateLongObjectField");
        runWithOperation(dataDirectory, p -> p.setLongObjectField(42L));
    }
    
    @RepeatedTest(3)
    void updateDoubleObjectField() {
        Path dataDirectory = Path.of("target", "updateDoubleObjectField");
        runWithOperation(dataDirectory, p -> p.setDoubleObjectField(42.0));
    }
    
    @RepeatedTest(3)
    void updateBooleanObjectField() {
        Path dataDirectory = Path.of("target", "updateBooleanObjectField");
        runWithOperation(dataDirectory, p -> p.setBooleanObjectField(true));
    }
    
    @RepeatedTest(3)
    void updateCharObjectField() {
        Path dataDirectory = Path.of("target", "updateCharObjectField");
        runWithOperation(dataDirectory, p -> p.setCharObjectField('a'));
    }
    
    @RepeatedTest(3)
    void updateByteObjectField() {
        Path dataDirectory = Path.of("target", "updateByteObjectField");
        runWithOperation(dataDirectory, p -> p.setByteObjectField((byte) 42));
    }
    
    @RepeatedTest(3)
    void updateShortObjectField() {
        Path dataDirectory = Path.of("target", "updateShortObjectField");
        runWithOperation(dataDirectory, p -> p.setShortObjectField((short) 42));
    }
    
    @RepeatedTest(3)
    void updateFloatObjectField() {
        Path dataDirectory = Path.of("target", "updateFloatObjectField");
        runWithOperation(dataDirectory, p -> p.setFloatObjectField(42.0f));
    }
    
    @RepeatedTest(3)
    void updateLocalDateField() {
        Path dataDirectory = Path.of("target", "updateBigIntegerField");
        runWithOperation(dataDirectory, p -> p.setLocalDateField(LocalDate.now()));
    }
    
    @RepeatedTest(3)
    void updateLocalDateTimeField() {
        Path dataDirectory = Path.of("target", "updateLocalDateTimeField");
        runWithOperation(dataDirectory, p -> p.setLocalDateTimeField(LocalDateTime.now()));
    }
    
    @RepeatedTest(3)
    void updateLocalTimeField()
    {
        Path dataDirectory = Path.of("target", "updateLocalTimeField");
        runWithOperation(dataDirectory, p -> p.setLocalTimeField(LocalDateTime.now().toLocalTime()));
    }
    
    @RepeatedTest(3)
    void updateUUIDField()
    {
        Path dataDirectory = Path.of("target", "updateUUIDField");
        runWithOperation(dataDirectory, p -> p.setUuidField(java.util.UUID.randomUUID()));
    }
    
    @RepeatedTest(3)
    void updateYearMonthField()
    {
        Path dataDirectory = Path.of("target", "updateYearMonthField");
        runWithOperation(dataDirectory, p -> p.setYearMonthField(YearMonth.now()));
    }
    
    
    
    private AllTypesPojo getRandomPojo(GigaMap<AllTypesPojo> map)
    {
        return map.get(ThreadLocalRandom.current().nextInt(0, MAX_SIZE));
    }
    
    private void runWithOperation(Path storagePath, Consumer<AllTypesPojo> operation)
    {
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(storagePath)) {
            
            if (storageManager.root() == null) {
                AllTypeIndicesMapCreator mapCreator = new AllTypeIndicesMapCreator();
                GigaMap<AllTypesPojo> map = mapCreator.generateMap(MAX_SIZE);
                storageManager.setRoot(map);
                storageManager.storeRoot();
            } else {
                GigaMap<AllTypesPojo> map = (GigaMap<AllTypesPojo>) storageManager.root();

                AllTypesPojo pojo = getRandomPojo(map);
                map.update(pojo, operation);
                storageManager.storeAll(map, pojo);
            }
        }
    }
}
