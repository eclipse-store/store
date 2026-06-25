package test.eclipse.store.zombie;

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
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.eclipse.serializer.persistence.exceptions.PersistenceExceptionConsistencyObjectId;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.store.storage.analysis.*;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageAdjacencyDataExporter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import test.eclipse.store.library.TypeEnum;

public class StorerWithIdTest
{
    @TempDir
    Path tempDir;

    public static String value = "hello";

    @Test
    void saveNewObject()
    {
        String root = "ahoj";

        Root newRoot = new Root();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(newRoot, tempDir)) {

            long l = storageManager.persistenceManager().objectRegistry().lookupObjectId(newRoot);

            Storer storer = storageManager.createStorer();
            long storeId = storer.store(root, l + 10);
            assertEquals(storeId, l + 10);
            storer.commit();

            boolean hello = storageManager.persistenceManager().objectRegistry().isValid(storeId, root);
            assertTrue(hello, "Object should be valid after storing");
        }

    }

    @Test
    void saveExistingObject_Exception()
    {
        Root newRoot = new Root();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(newRoot, tempDir)) {

            long l = storageManager.persistenceManager().objectRegistry().lookupObjectId(newRoot);

            Storer storer = storageManager.createStorer();
            long storeId = storer.store(newRoot, l + 10);
            assertEquals(storeId, l + 10);
            assertThrows(PersistenceExceptionConsistencyObjectId.class, storer::commit);


            long store = storageManager.store(newRoot);
            assertEquals(l, store, "Stored ID should match original object ID");

            boolean b = storageManager.persistenceManager().objectRegistry().containsObjectId(1000000000000000038L);
            assertFalse(b, "Object should not be valid after storing with different ID");

        }

    }

    @Test
    void saveSameObjectAgain()
    {
        Root newRoot = new Root();
        String root = "ahoj";

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(newRoot, tempDir)) {

            long l = storageManager.persistenceManager().objectRegistry().lookupObjectId(newRoot);

            Storer storer = storageManager.createStorer();
            long storeId = storer.store(newRoot, l);
            assertEquals(storeId, l, "Stored ID should match original object ID");
            storer.commit();

        }

    }

    @Test
    void saveAnotherObjectOnTheSameId_Exception()
    {
        Root newRoot = new Root();
        String root = "hello";

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(newRoot, tempDir)) {

            long l = storageManager.persistenceManager().objectRegistry().lookupObjectId(newRoot);

            Storer storer = storageManager.createStorer();
            long storeId = storer.store(root, l);
            assertEquals(storeId, l, "Stored ID should match original object ID");
            assertThrows(PersistenceExceptionConsistencyObjectId.class, storer::commit);
        }

    }

    @Test
    void findMissingObject(@TempDir Path exportDir)
    {
        ListRoot newRoot = new ListRoot();
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(newRoot, tempDir)) {
            final List<StorageAdjacencyDataExporter.AdjacencyFiles> exports = storage.exportAdjacencyData(exportDir);

            final AdjacencyDataConverter dataPreparator = AdjacencyDataConverter.New(exports);
            final AdjacencyDataConverter.ConvertedAdjacencyFiles data = dataPreparator.convert();

            final MissingObjectsSearch analyser = MissingObjectsSearch.New(exports, data.getReferenceSets(), null);
            final MissingObjects missingEntities = analyser.searchMissingEntities();
          
            Assertions.assertEquals(0, missingEntities.getMissingObjectIDs().size());

            final ReverseObjectSearch reverseObjectSearch = ReverseObjectSearch.New(exports, data);
            Long objectId = storage.persistenceManager().objectRegistry().lookupObjectId(newRoot.getList().get(3));

            ObjectParents objectParents = reverseObjectSearch.searchObjectIDs(Set.of(objectId));

            Arrays.stream(objectParents.getParents(objectId)).forEach((id) -> {
                Object o = storage.persistenceManager().objectRegistry().lookupObject(id);
                assertSame(newRoot.getList(), o);
            });


        }

    }

    @ParameterizedTest()
    @EnumSource(TypeEnum.class)
    void name(TypeEnum type, @TempDir Path exportDir)
    {

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(type.getOriginal(), tempDir)) {
            final List<StorageAdjacencyDataExporter.AdjacencyFiles> exports = storage.exportAdjacencyData(exportDir);

            final AdjacencyDataConverter dataPreparator = AdjacencyDataConverter.New(exports);
            final AdjacencyDataConverter.ConvertedAdjacencyFiles data = dataPreparator.convert();


            final MissingObjectsSearch analyser = MissingObjectsSearch.New(exports, data.getReferenceSets(), null);
            final MissingObjects missingEntities = analyser.searchMissingEntities();

            assertEquals(0, missingEntities.getMissingObjectIDs().size());
//            missingEntities.getMissingObjectIDs().forEach(aLong -> {
//                Object o = storage.persistenceManager().objectRegistry().lookupObject(aLong);
//                System.out.println("Missing Objects: " + o);
//            });

            long l1 = storage.persistenceManager().currentObjectId();

            final ReverseObjectSearch reverseObjectSearch = ReverseObjectSearch.New(exports, data);
            ObjectParents objectParents = reverseObjectSearch.searchObjectIDs(Set.of(l1));

            assertNotEquals(0, objectParents.getParents(l1).length);

//            long l = storage.persistenceManager().objectRegistry().lookupObjectId(type);
//            System.out.println(l);
//
//            System.out.println("Current Object ID: " + l1);
//            Object o = storage.persistenceManager().objectRegistry().lookupObject(l1);
//            System.out.println("Current Object: " + o);
//
//            long[] parents = objectParents.getParents(l1);
//            System.out.println("Parents of current object ID: " + Arrays.toString(parents));
//
//            System.out.println("==========================================================");


        }

    }


    private static class ListRoot
    {
        List<String> list = new ArrayList<>();
        String s = "hello";

        public ListRoot()
        {
            list.add(s);
            list.add("world");
            list.add("test");
            list.add("microstream");
            list.add("zombie");
        }

        @Override
        public String toString()
        {
            return "ListRoot{" +
                    "list=" + list +
                    ", s='" + s + '\'' +
                    '}';
        }

        public List<String> getList()
        {
            return list;
        }
    }

    private static class Root
    {
        String root = value;

}
}
