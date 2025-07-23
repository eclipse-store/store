package org.eclipse.store.gigamap.restart.update;

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
import java.nio.file.Paths;

import static org.eclipse.store.gigamap.restart.update.SmalEVIndices.*;

public class SmallEVTest
{

    Path tempDir = Paths.get("target", "smallEVH");

    @RepeatedTest(5)
    void testWithUpdateAndRestart()
    {
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            GigaMap<SmallEV> smallEVs;
            if (storageManager.root() == null) {
                smallEVs = generateData();
                storageManager.setRoot(smallEVs);
                storageManager.storeRoot();
                System.out.println("Stored to storage: " + smallEVs.size());
            } else {
                smallEVs = (GigaMap<SmallEV>) storageManager.root();
                System.out.println("Loaded from storage: " + smallEVs.size());
            }
            SmallEV smallEV = smallEVs.get(0);
            smallEVs.update(smallEV, smallEV1 -> smallEV1.setElectric(false));
            storageManager.storeAll(smallEVs, smallEV);
        }
    }



    private GigaMap<SmallEV> generateData() {
        GigaMap<SmallEV> smallEVs = GigaMap.New();
        smallEVs.index().bitmap().addAll(vin, make, model, year, electric);

        for (int i = 0; i < 10; i++) {
            SmallEV smallEV = new SmallEV("vin" + i);
            smallEV.setMake("make" + i);
            smallEV.setModel("model" + i);
            smallEV.setYear(2000 + i);
            smallEV.setElectric(i % 2 == 0);
            smallEVs.add(smallEV);
        }
        return smallEVs;
    }

}
