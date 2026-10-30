package test.microstream;

/*-
 * #%L
 * EclipseStore Integration Tests - JDK - Sealed Classes
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


import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

public class StoreSealedTest {

    @TempDir
    Path tempDir;

    private static String CAR_REGISTRATION_NUMBER = "WEN 2031";

    @Test
    public void storeAndLoadSealed() {
        Car car = new Car(5, CAR_REGISTRATION_NUMBER );
        Truck truck = new Truck(1000, "2SI 3018");

        Root root = new Root(car, truck);

        Root copyRoot = new Root();

        EmbeddedStorageManager storageManager = EmbeddedStorage.start(root,tempDir );


        storageManager.close();

        storageManager = EmbeddedStorage.start(copyRoot,tempDir );
        storageManager.close();

        Assertions.assertEquals(CAR_REGISTRATION_NUMBER, copyRoot.getCar().getRegistrationNumber());


    }
}
