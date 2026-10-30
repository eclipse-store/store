package one.microstream.kotlin


import org.eclipse.store.storage.embedded.types.EmbeddedStorage
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import test.microstream.library.TypeEnum
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
