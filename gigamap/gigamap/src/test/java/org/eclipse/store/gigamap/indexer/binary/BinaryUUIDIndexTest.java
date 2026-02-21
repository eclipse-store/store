package org.eclipse.store.gigamap.indexer.binary;

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

import org.eclipse.store.gigamap.types.BinaryIndexerUUID;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class BinaryUUIDIndexTest
{
    @TempDir
    Path tempDir;

    @Test
    void uuidPersonTest()
    {
        GigaMap<UUIDPerson> map = GigaMap.<UUIDPerson>Builder()
            .withBitmapIdentityIndex(uuidPersonIndex)
            .build();
        
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        UUID zeroMsb = new UUID(0L, 42L);
        UUID zeroLsb = new UUID(42L, 0L);
        UUID zeroBoth = new UUID(0L, 0L);

        //generate some data
        UUIDPerson person1 = new UUIDPerson("Alice", uuid1);
        UUIDPerson person2 = new UUIDPerson("Bob", uuid2);
        UUIDPerson person3 = new UUIDPerson("Charlie", UUID.randomUUID());
        UUIDPerson person4 = new UUIDPerson("Dave", zeroMsb);
        UUIDPerson person5 = new UUIDPerson("Eve", zeroLsb);
        UUIDPerson person6 = new UUIDPerson("Frank", zeroBoth);
        map.addAll(person1, person2, person3, person4, person5, person6);

        assertEquals(uuid1, map.query(uuidPersonIndex.is(uuid1)).findFirst().get().uuid);
        assertEquals(uuid2, map.query(uuidPersonIndex.is(uuid2)).findFirst().get().uuid);
        assertEquals(zeroMsb, map.query(uuidPersonIndex.is(zeroMsb)).findFirst().get().uuid);
        assertEquals(zeroLsb, map.query(uuidPersonIndex.is(zeroLsb)).findFirst().get().uuid);
        assertEquals(zeroBoth, map.query(uuidPersonIndex.is(zeroBoth)).findFirst().get().uuid);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {

            assertEquals(uuid1, map.query(uuidPersonIndex.is(uuid1)).findFirst().get().uuid);
            assertEquals(uuid2, map.query(uuidPersonIndex.is(uuid2)).findFirst().get().uuid);
            assertEquals(zeroMsb, map.query(uuidPersonIndex.is(zeroMsb)).findFirst().get().uuid);
            assertEquals(zeroLsb, map.query(uuidPersonIndex.is(zeroLsb)).findFirst().get().uuid);
            assertEquals(zeroBoth, map.query(uuidPersonIndex.is(zeroBoth)).findFirst().get().uuid);

            map.query(uuidPersonIndex.is(uuid1)).and(uuidPersonIndex.is(uuid2))
                    .forEach(uuidPerson -> fail("Should not be reached"));

            List<UUIDPerson> personList = map.query(uuidPersonIndex.is(uuid1)).or(uuidPersonIndex.is(uuid2))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<UUIDPerson> newMap = (GigaMap<UUIDPerson>) manager.root();
            newMap.query(uuidPersonIndex.is(uuid1)).forEach(uuidPerson -> assertEquals(uuid1, uuidPerson.getUuid()));
            newMap.query(uuidPersonIndex.is(uuid2)).forEach(uuidPerson -> assertEquals(uuid2, uuidPerson.getUuid()));
            newMap.query(uuidPersonIndex.is(zeroMsb)).forEach(uuidPerson -> assertEquals(zeroMsb, uuidPerson.getUuid()));
            newMap.query(uuidPersonIndex.is(zeroLsb)).forEach(uuidPerson -> assertEquals(zeroLsb, uuidPerson.getUuid()));
            newMap.query(uuidPersonIndex.is(zeroBoth)).forEach(uuidPerson -> assertEquals(zeroBoth, uuidPerson.getUuid()));

            newMap.query(uuidPersonIndex.is(uuid1)).and(uuidPersonIndex.is(uuid2))
                    .forEach(uuidPerson -> fail("Should not be reached"));

            List<UUIDPerson> personList = newMap.query(uuidPersonIndex.is(uuid1)).or(uuidPersonIndex.is(uuid2))
                            .toList();
            assertEquals(2, personList.size());
        }
    }

    static UUIDPersonIndex uuidPersonIndex = new UUIDPersonIndex();
    
    static class UUIDPersonIndex extends BinaryIndexerUUID.Abstract<UUIDPerson>
    {

        @Override
        protected UUID getUUID(UUIDPerson entity)
        {
            return entity.getUuid();
        }
    }

    static class UUIDPerson
    {
        private final String name;
        private final UUID uuid;

        public UUIDPerson(String name, UUID uuid)
        {
            this.name = name;
            this.uuid = uuid;
        }

        public String name()
        {
            return this.name;
        }

        public UUID getUuid()
        {
            return this.uuid;
        }
        
        @Override
        public String toString()
        {
            return "UUIDPerson{" +
                "name='" + name + '\'' +
                ", uuid=" + uuid +
                '}';
        }
    }
}
