package test.eclipse.store.deepcopy;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.serializer.ObjectCopier;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Deep-copy tests focused on Lazy references: loaded, cleared, null,
 * shared, nested, circular, inside collections. Goal is to surface
 * defects, not produce a green run.
 */

@Disabled("https://github.com/microstream-one/internal/issues/53")
public class DeepCopyLazyTest
{

    EmbeddedStorageManager storageManager;

    @TempDir
    Path tempDir;

    @AfterEach
    public void afterTest()
    {
        if (storageManager != null) {
            storageManager.shutdown();
        }
    }

    @Test
    public void loadedLazy_subjectIsDeepCopied()
    {
        Holder root = new Holder("root");
        root.lazyPerson = Lazy.Reference(new Person("Alice", 30));

        storageManager = EmbeddedStorage.start(root, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        Holder copy = objectCopier.copy(root);

        assertNotSame(root, copy);
        assertNotNull(copy.lazyPerson);
        assertNotSame(root.lazyPerson, copy.lazyPerson);
        assertNotSame(root.lazyPerson.get(), copy.lazyPerson.get());
        assertEquals("Alice", copy.lazyPerson.get().name);
        assertEquals(30, copy.lazyPerson.get().age);
    }

    @Test
    public void loadedLazy_wrapperTypeIsPreserved()
    {
        Holder root = new Holder("root");
        root.lazyPerson = Lazy.Reference(new Person("Bob", 40));

        storageManager = EmbeddedStorage.start(root, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        Holder copy = objectCopier.copy(root);

        // The Lazy<T> wrapper must survive — it must not be silently
        // unwrapped to its subject during deep copy.
        assertTrue(copy.lazyPerson instanceof Lazy);
    }

    @Test
    public void loadedLazy_mutationDoesNotLeakBack()
    {
        Holder root = new Holder("root");
        root.lazyPerson = Lazy.Reference(new Person("Carol", 25));

        storageManager = EmbeddedStorage.start(root, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        Holder copy = objectCopier.copy(root);

        copy.lazyPerson.get().name = "MUTATED";
        copy.lazyPerson.get().age = 999;

        assertEquals("Carol", root.lazyPerson.get().name);
        assertEquals(25, root.lazyPerson.get().age);
    }

    @Test
    public void loadedLazy_originalIsNotForceClearedByCopy()
    {
        Holder root = new Holder("root");
        root.lazyPerson = Lazy.Reference(new Person("Dave", 50));

        storageManager = EmbeddedStorage.start(root, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        objectCopier.copy(root);

        // Deep copy must not clear/unload the source graph as a side effect.
        assertNotNull(root.lazyPerson.peek());
        assertTrue(root.lazyPerson.isLoaded());
    }

    @Test
    public void lazyOfNull_deepCopy()
    {
        Holder root = new Holder("root");
        root.lazyPerson = Lazy.Reference(null);

        storageManager = EmbeddedStorage.start(root, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        Holder copy = objectCopier.copy(root);

        // Lazy.Reference(null) must round-trip as a Lazy wrapping null,
        // not be collapsed to a plain null field on the copy.
        assertNotNull(copy.lazyPerson);
        assertNull(copy.lazyPerson.peek());
        assertNull(copy.lazyPerson.get());
    }

    @Test
    public void sharedLazyWrapper_identityIsPreserved()
    {
        Lazy<Person> shared = Lazy.Reference(new Person("Eve", 28));
        TwoLazyHolder root = new TwoLazyHolder();
        root.left = shared;
        root.right = shared;

        storageManager = EmbeddedStorage.start(root, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        TwoLazyHolder copy = objectCopier.copy(root);

        // Two fields pointing at the same Lazy instance must remain
        // a single shared instance after deep copy.
        assertSame(copy.left, copy.right);
    }

    @Test
    public void sharedLazySubject_identityIsPreserved()
    {
        Person samePerson = new Person("Frank", 60);
        TwoLazyHolder root = new TwoLazyHolder();
        root.left = Lazy.Reference(samePerson);
        root.right = Lazy.Reference(samePerson);

        storageManager = EmbeddedStorage.start(root, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        TwoLazyHolder copy = objectCopier.copy(root);

        assertNotSame(copy.left, copy.right);
        // Two distinct Lazy wrappers that referenced the same subject
        // must, in the copy, still resolve to one and the same subject.
        assertSame(copy.left.get(), copy.right.get());
    }

    @Test
    public void copiedLazy_isNotStored()
    {
        Holder root = new Holder("root");
        root.lazyPerson = Lazy.Reference(new Person("Greta", 33));

        storageManager = EmbeddedStorage.start(root, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        Holder copy = objectCopier.copy(root);

        // ObjectCopier never persists the result to a real storage.
        assertFalse(copy.lazyPerson.isStored());
    }

    @Test
    public void copiedLazy_isLoaded()
    {
        Holder root = new Holder("root");
        root.lazyPerson = Lazy.Reference(new Person("Hugo", 41));

        storageManager = EmbeddedStorage.start(root, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        Holder copy = objectCopier.copy(root);

        assertTrue(copy.lazyPerson.isLoaded());
    }

    @Test
    public void copiedLazy_clearThrows_butForceClearWorks()
    {
        Holder root = new Holder("root");
        root.lazyPerson = Lazy.Reference(new Person("Ivan", 22));

        storageManager = EmbeddedStorage.start(root, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        Holder copy = objectCopier.copy(root);

        // Per Lazy contract: clear() requires isStored(), forceClear() does not.
        assertThrows(IllegalStateException.class, () -> copy.lazyPerson.clear());
        assertDoesNotThrow(() -> copy.lazyPerson.forceClear());
        assertNull(copy.lazyPerson.peek());
    }

    @Test
    public void lazyInsideList_deepCopy()
    {
        List<Lazy<Person>> list = new ArrayList<>();
        list.add(Lazy.Reference(new Person("L1", 1)));
        list.add(Lazy.Reference(new Person("L2", 2)));
        list.add(Lazy.Reference(new Person("L3", 3)));

        storageManager = EmbeddedStorage.start(list, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        List<Lazy<Person>> copy = objectCopier.copy(list);

        assertEquals(3, copy.size());
        for (int i = 0; i < list.size(); i++) {
            assertNotSame(list.get(i), copy.get(i));
            assertNotSame(list.get(i).get(), copy.get(i).get());
            assertEquals(list.get(i).get().name, copy.get(i).get().name);
        }
    }

    @Test
    public void lazyInsideMap_deepCopy()
    {
        Map<String, Lazy<Person>> map = new HashMap<>();
        map.put("a", Lazy.Reference(new Person("A", 10)));
        map.put("b", Lazy.Reference(new Person("B", 20)));

        storageManager = EmbeddedStorage.start(map, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        Map<String, Lazy<Person>> copy = objectCopier.copy(map);

        assertEquals(2, copy.size());
        assertEquals("A", copy.get("a").get().name);
        assertEquals("B", copy.get("b").get().name);
        assertNotSame(map.get("a"), copy.get("a"));
        assertNotSame(map.get("a").get(), copy.get("a").get());
    }

    @Test
    public void lazyContainsCollection_deepCopy()
    {
        List<Person> people = new ArrayList<>();
        people.add(new Person("X", 1));
        people.add(new Person("Y", 2));

        CollectionHolder root = new CollectionHolder();
        root.lazyList = Lazy.Reference(people);

        storageManager = EmbeddedStorage.start(root, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        CollectionHolder copy = objectCopier.copy(root);

        assertNotSame(root.lazyList, copy.lazyList);
        assertNotSame(root.lazyList.get(), copy.lazyList.get());
        assertEquals(2, copy.lazyList.get().size());

        copy.lazyList.get().add(new Person("Z", 3));
        assertEquals(2, root.lazyList.get().size());
    }

    @Test
    public void nestedLazy_deepCopy()
    {
        Holder inner = new Holder("inner");
        inner.lazyPerson = Lazy.Reference(new Person("Nested", 7));

        NestedHolder root = new NestedHolder();
        root.lazyHolder = Lazy.Reference(inner);

        storageManager = EmbeddedStorage.start(root, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        NestedHolder copy = objectCopier.copy(root);

        assertNotSame(root.lazyHolder, copy.lazyHolder);
        assertNotSame(root.lazyHolder.get(), copy.lazyHolder.get());
        assertNotSame(root.lazyHolder.get().lazyPerson, copy.lazyHolder.get().lazyPerson);
        assertEquals("Nested", copy.lazyHolder.get().lazyPerson.get().name);

        copy.lazyHolder.get().lazyPerson.get().name = "MUTATED";
        assertEquals("Nested", root.lazyHolder.get().lazyPerson.get().name);
    }

    @Test
    public void circularGraphThroughLazy_deepCopy()
    {
        CircularNode a = new CircularNode("A");
        CircularNode b = new CircularNode("B");
        a.next = Lazy.Reference(b);
        b.next = Lazy.Reference(a);

        storageManager = EmbeddedStorage.start(a, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        CircularNode copy = objectCopier.copy(a);

        assertEquals("A", copy.name);
        assertEquals("B", copy.next.get().name);
        // The cycle must round-trip as a real cycle, not as a duplicated A.
        assertSame(copy, copy.next.get().next.get());
    }

    @Test
    public void sameLazyCopiedTwice_copiesAreIndependent()
    {
        Holder root = new Holder("root");
        root.lazyPerson = Lazy.Reference(new Person("Multi", 99));

        storageManager = EmbeddedStorage.start(root, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        Holder c1 = objectCopier.copy(root);
        Holder c2 = objectCopier.copy(root);

        assertNotSame(c1.lazyPerson, c2.lazyPerson);
        assertNotSame(c1.lazyPerson.get(), c2.lazyPerson.get());

        c1.lazyPerson.get().name = "C1";
        c2.lazyPerson.get().name = "C2";
        assertEquals("Multi", root.lazyPerson.get().name);
    }

    @Test
    public void unloadedLazy_deepCopy_subjectIsRecovered()
    {
        // Bug hunt: a Lazy that is stored and then cleared has its subject
        // only on disk — ObjectCopier creates its own PersistenceManager
        // and does not share the storage's loader, so this is exactly the
        // place where data may silently be lost.
        Holder root = new Holder("root");
        root.lazyPerson = Lazy.Reference(new Person("Storage-resident", 77));

        storageManager = EmbeddedStorage.start(root, tempDir);

        assertTrue(root.lazyPerson.isStored());
        root.lazyPerson.clear();
        assertNull(root.lazyPerson.peek());
        assertFalse(root.lazyPerson.isLoaded());

        ObjectCopier objectCopier = ObjectCopier.New();
        Holder copy = objectCopier.copy(root);

        assertNotNull(copy.lazyPerson);
        Person recovered = copy.lazyPerson.get();
        assertNotNull(recovered);
        assertEquals("Storage-resident", recovered.name);
        assertEquals(77, recovered.age);
    }

    @Test
    public void unloadedLazy_deepCopy_originalRemainsCleared()
    {
        Holder root = new Holder("root");
        root.lazyPerson = Lazy.Reference(new Person("Stays-cleared", 55));

        storageManager = EmbeddedStorage.start(root, tempDir);
        root.lazyPerson.clear();

        ObjectCopier objectCopier = ObjectCopier.New();
        objectCopier.copy(root);

        // Deep copy must not silently re-populate the original cleared Lazy.
        assertNull(root.lazyPerson.peek());
    }

    @Test
    public void unloadedLazy_insideContainer_deepCopy()
    {
        // Mixed state inside a single graph: some lazies loaded, one cleared.
        // All must round-trip without losing the cleared subject.
        List<Lazy<Person>> list = new ArrayList<>();
        list.add(Lazy.Reference(new Person("Loaded-1", 1)));
        list.add(Lazy.Reference(new Person("Cleared-2", 2)));
        list.add(Lazy.Reference(new Person("Loaded-3", 3)));

        storageManager = EmbeddedStorage.start(list, tempDir);
        list.get(1).clear();
        assertNull(list.get(1).peek());

        ObjectCopier objectCopier = ObjectCopier.New();
        List<Lazy<Person>> copy = objectCopier.copy(list);

        assertEquals(3, copy.size());
        assertEquals("Loaded-1", copy.get(0).get().name);
        Person mid = copy.get(1).get();
        assertNotNull(mid);
        assertEquals("Cleared-2", mid.name);
        assertEquals("Loaded-3", copy.get(2).get().name);
    }

    static class Person
    {
        String name;
        int age;
        Person friend;

        Person(String name, int age)
        {
            this.name = name;
            this.age = age;
        }
    }

    static class Holder
    {
        String label;
        Lazy<Person> lazyPerson;

        Holder()
        {
        }

        Holder(String label)
        {
            this.label = label;
        }
    }

    static class TwoLazyHolder
    {
        Lazy<Person> left;
        Lazy<Person> right;
    }

    static class NestedHolder
    {
        Lazy<Holder> lazyHolder;
    }

    static class CollectionHolder
    {
        Lazy<List<Person>> lazyList;
    }

    static class CircularNode
    {
        String name;
        Lazy<CircularNode> next;

        CircularNode(String name)
        {
            this.name = name;
        }
    }
}
