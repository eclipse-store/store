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
import java.util.function.Function;

public class AllTypesApplyTest
{
    private static final int MAX_SIZE = 200;


    @RepeatedTest(3)
    void applyString()
    {
        Path dataDirectory = Path.of("target", "applyString");
        runWithOperation(dataDirectory, p -> {
            p.setStringField("Hello World");
            return p;
        });
    }

    @RepeatedTest(3)
    void applyIntField()
    {
        Path dataDirectory = Path.of("target", "applyIntField");
        runWithOperation(dataDirectory, p -> {
            p.setIntField(42);
            return p;
        });
    }

    @RepeatedTest(3)
    void applyLongField()
    {
        Path dataDirectory = Path.of("target", "applyLongField");
        runWithOperation(dataDirectory, p -> {
            p.setLongField(45L);
            return p;
        });
    }

    @RepeatedTest(3)
    void applyDoubleField()
    {
        Path dataDirectory = Path.of("target", "applyDoubleField");
        runWithOperation(dataDirectory, p -> {
            p.setDoubleField(42.0);
            return p;
        });
    }

    @RepeatedTest(3)
    void applyBooleanField()
    {
        Path dataDirectory = Path.of("target", "applyBooleanField");
        runWithOperation(dataDirectory, p -> {
            p.setBooleanField(false);
            return p;
        });

    }

    @RepeatedTest(3)
    void applyCharField()
    {
        Path dataDirectory = Path.of("target", "applyCharField");
        runWithOperation(dataDirectory, p -> {
            p.setCharField('a');
            return p;
        });
    }

    @RepeatedTest(3)
    void applyByteField()
    {
        Path dataDirectory = Path.of("target", "applyByteField");
        runWithOperation(dataDirectory, p -> {
            p.setByteField((byte) 42);
            return p;
        });
    }

    @RepeatedTest(3)
    void applyShortField()
    {
        Path dataDirectory = Path.of("target", "applyShortField");
        runWithOperation(dataDirectory, p -> {
            p.setShortField((short) 42);
            return p;
        });
    }

    @RepeatedTest(3)
    void applyFloatField()
    {
        Path dataDirectory = Path.of("target", "applyFloatField");
        runWithOperation(dataDirectory, p -> {
            p.setFloatField(42.0f);
            return p;
        });
    }

    @RepeatedTest(3)
    void applyIntegerField()
    {
        Path dataDirectory = Path.of("target", "applyIntegerField");
        runWithOperation(dataDirectory, p -> {
            p.setIntegerField(42);
            return p;
        });
    }

    @RepeatedTest(3)
    void applyLongObjectField()
    {
        Path dataDirectory = Path.of("target", "applyLongObjectField");
        runWithOperation(dataDirectory, p -> {
            p.setLongObjectField(42L);
            return p;
        });
    }

    @RepeatedTest(3)
    void applyDoubleObjectField()
    {
        Path dataDirectory = Path.of("target", "applyDoubleObjectField");
        runWithOperation(dataDirectory, p -> {
            p.setDoubleObjectField(42.0);
            return p;
        });
    }

    @RepeatedTest(3)
    void applyBooleanObjectField()
    {
        Path dataDirectory = Path.of("target", "applyBooleanObjectField");
        runWithOperation(dataDirectory, p -> {
            p.setBooleanObjectField(true);
            return p;
        });
    }

    @RepeatedTest(3)
    void applyCharObjectField()
    {
        Path dataDirectory = Path.of("target", "applyCharObjectField");
        runWithOperation(dataDirectory, p -> {
            p.setCharObjectField('a');
            return p;
        });
    }

    @RepeatedTest(3)
    void applyByteObjectField()
    {
        Path dataDirectory = Path.of("target", "applyByteObjectField");
        runWithOperation(dataDirectory, p -> {
            p.setByteObjectField((byte) 42);
            return p;
        });
    }

    @RepeatedTest(3)
    void applyShortObjectField()
    {
        Path dataDirectory = Path.of("target", "applyShortObjectField");
        runWithOperation(dataDirectory, p -> {
            p.setShortObjectField((short) 42);
            return p;
        });
    }

    @RepeatedTest(3)
    void applyFloatObjectField()
    {
        Path dataDirectory = Path.of("target", "applyFloatObjectField");
        runWithOperation(dataDirectory, p -> {
            p.setFloatObjectField(42.0f);
            return p;
        });
    }

    @RepeatedTest(3)
    void applyLocalDateField()
    {
        Path dataDirectory = Path.of("target", "applyBigIntegerField");
        runWithOperation(dataDirectory, p -> {
            p.setLocalDateField(LocalDate.now());
            return p;
        });
    }

    @RepeatedTest(3)
    void applyLocalDateTimeField()
    {
        Path dataDirectory = Path.of("target", "applyLocalDateTimeField");
        runWithOperation(dataDirectory, p -> {
            p.setLocalDateTimeField(LocalDateTime.now());
            return p;
        });
    }

    @RepeatedTest(3)
    void applyLocalTimeField()
    {
        Path dataDirectory = Path.of("target", "applyLocalTimeField");
        runWithOperation(dataDirectory, p -> {
            p.setLocalTimeField(LocalDateTime.now().toLocalTime());
            return p;
        });

    }

    // don't update identifier index for now, needs further investigation
//    @RepeatedTest(3)
//    void applyUUIDField()
//    {
//        Path dataDirectory = Path.of("target", "applyUUIDField");
//        runWithOperation(dataDirectory, p -> {
//            p.setUuidField(java.util.UUID.randomUUID());
//            return p;
//        });
//    }

    @RepeatedTest(3)
    void applyYearMonthField()
    {
        Path dataDirectory = Path.of("target", "applyYearMonthField");
        runWithOperation(dataDirectory, p -> {
            p.setYearMonthField(YearMonth.now());
            return p;
        });
    }


    private AllTypesPojo getRandomPojo(GigaMap<AllTypesPojo> map)
    {
        return map.get(ThreadLocalRandom.current().nextInt(0, MAX_SIZE));
    }

    private void runWithOperation(Path storagePath, Function<AllTypesPojo, AllTypesPojo> operation)
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
                AllTypesPojo toReplacePojo = new AllTypesPojo();
                toReplacePojo.generateRandomData();
                map.apply(pojo, operation);
                map.store();
            }
        }
    }
}
