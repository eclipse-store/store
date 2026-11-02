package test.eclipse.store.kotlin

/*-
 * #%L
 * EclipseStore Integration Tests - JDK - Kotlin
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


import org.eclipse.store.storage.embedded.types.EmbeddedStorage
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import test.eclipse.store.library.TypeEnum
import java.nio.file.Path

class TypeTest {

    private var actualDirectory: Path? = null


    private fun startStorage(root: Any?): EmbeddedStorageManager? {
        return EmbeddedStorage.start(root, actualDirectory)
    }

    private var storage: EmbeddedStorageManager? = null


//    typeRegister.proveData(transferedTypeRegister,"hashTableMSData", "identityHashMapData", "lazyData", "localDateData",
//    "localDateTimeData", "calendarApiData"
//    );


    @ParameterizedTest
    @EnumSource(TypeEnum::class)
    fun saveAndLoadTest(type: TypeEnum, @TempDir targetDirectory : Path ) {
        //println(type.original.javaClass.simpleName)
        val o = saveToMSAndReload(type.original, type.createEmptyInstance(), targetDirectory)
        type.original.proveResults(o)
        storageShutdown()
    }

    private fun storageShutdown() {
        storage!!.shutdown()
    }

    private fun saveToMSAndReload(original: Any?, loaded: Any?, targetDirectory: Path): Any? {
        actualDirectory = targetDirectory.resolve(System.currentTimeMillis().toString())
        storage = startStorage(original)
        storage!!.storeRoot()
        storage!!.shutdown()
        storage = startStorage(loaded)
        return loaded
    }
}
