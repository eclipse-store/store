package one.microstream.kotlin

import org.eclipse.store.storage.embedded.types.EmbeddedStorage
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

class KotlinCollectionTest {

    @Test
    fun mutableListTest(@TempDir targetDirectory : Path) {

        val buffer = mutableListOf("one", "two", "third");
        val copy = mutableListOf<String>();

        var storage = EmbeddedStorage.start(buffer, targetDirectory);
        storage.shutdown();

        storage = EmbeddedStorage.start(copy, targetDirectory);


        Assertions.assertIterableEquals(buffer, copy);

        Assertions.assertTrue(copy == buffer);

        copy.add("fourth")

        storage.store(copy);

        storage.shutdown();

        storage = EmbeddedStorage.start(buffer, targetDirectory);

        storage.shutdown();

        assertEquals(4, buffer.size);
     //   println(buffer)
    }

    @Test
    fun mutableSetTest(@TempDir targetDirectory : Path) {

        val buffer = mutableSetOf("one", "two", "third");
        val copy = mutableSetOf<String>();

        var storage = EmbeddedStorage.start(buffer, targetDirectory);
        storage.shutdown();

        storage = EmbeddedStorage.start(copy, targetDirectory);
        storage.shutdown();

        Assertions.assertIterableEquals(buffer, copy);

        Assertions.assertTrue(copy == buffer);

    }

    @Test
    fun mutableMapTest(@TempDir targetDirectory : Path) {

        val buffer = mutableMapOf("key1" to 1, "key2" to 2, "key3" to 3, "key4" to 4)
        val copy = mutableMapOf<Int, String>();

        var storage = EmbeddedStorage.start(buffer, targetDirectory);
        storage.shutdown();

        storage = EmbeddedStorage.start(copy, targetDirectory);

        Assertions.assertTrue(copy == buffer);
        copy[5] = "kye5";
        storage.storeAll(copy, copy[5])
        storage.shutdown();

        buffer.clear();

        storage = EmbeddedStorage.start(buffer, targetDirectory);

        storage.shutdown();

        assertEquals(5, buffer.size)
        //println(buffer)
    }

}
