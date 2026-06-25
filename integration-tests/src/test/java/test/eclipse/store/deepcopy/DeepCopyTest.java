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
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


public class DeepCopyTest {

    EmbeddedStorageManager storageManager;

    @TempDir
    Path tempDir;

    @Test
    public void deepCopyTest() {

        List<DeepPerson> children = new ArrayList<>();

        DeepPerson father = new DeepPerson("Alex", "Samohoj", 190, true);
        DeepPerson mother = new DeepPerson("Alexandra", "Vesela", 160, false);
        DeepPerson son = new DeepPerson("Sohn", "Samohoj", 60, true, father, mother);
        children.add(son);

        storageManager = EmbeddedStorage.start(children, tempDir);

        ObjectCopier objectCopier = ObjectCopier.New();

        DeepPerson son2 = objectCopier.copy(son);

        DeepPerson anotherMother = new DeepPerson("Maria", "Veryclever", 170, false);
        son2.setMather(anotherMother);

        assertNotEquals(son.getMather(), son2.getMather());

        children.add(son2);

        assertNotEquals(children.get(0).getMather(), children.get(1).getMather());

    }

    @Test
    public void deepCopyWithNullParentsTest() {
        List<DeepPerson> people = new ArrayList<>();

        // Person with null parents
        DeepPerson orphan = new DeepPerson("Orphan", "Child", 120, true, null, null);
        people.add(orphan);

        storageManager = EmbeddedStorage.start(people, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        DeepPerson orphanCopy = objectCopier.copy(orphan);

        assertNotSame(orphan, orphanCopy);
        assertEquals(orphan.getFirstName(), orphanCopy.getFirstName());
        assertNull(orphanCopy.getFather());
        assertNull(orphanCopy.getMather());
    }

    @Test
    public void deepCopyCircularReferenceTest() {
        List<DeepPerson> people = new ArrayList<>();

        DeepPerson person1 = new DeepPerson("Person1", "First", 180, true);
        DeepPerson person2 = new DeepPerson("Person2", "Second", 170, false);

        // Create circular reference
        person1.setFather(person2);
        person2.setMather(person1);

        people.add(person1);

        storageManager = EmbeddedStorage.start(people, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        DeepPerson person1Copy = objectCopier.copy(person1);

        assertNotSame(person1, person1Copy);
        assertNotSame(person1.getFather(), person1Copy.getFather());
        assertNotNull(person1Copy.getFather());
        assertNotNull(person1Copy.getFather().getMather());
    }

    @Test
    public void deepCopyEmptyPersonTest() {
        List<DeepPerson> people = new ArrayList<>();

        DeepPerson emptyPerson = new DeepPerson(null, null, null, null);
        people.add(emptyPerson);

        storageManager = EmbeddedStorage.start(people, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        DeepPerson copy = objectCopier.copy(emptyPerson);

        assertNotSame(emptyPerson, copy);
        assertNull(copy.getFirstName());
        assertNull(copy.getSecondName());
        assertNull(copy.getHigh());
        assertNull(copy.getMan());
    }

    @Test
    public void deepCopyComplexFamilyTreeTest() {
        List<DeepPerson> family = new ArrayList<>();

        // Grandparents
        DeepPerson grandpa = new DeepPerson("Grandpa", "Smith", 170, true);
        DeepPerson grandma = new DeepPerson("Grandma", "Smith", 160, false);

        // Parents
        DeepPerson father = new DeepPerson("Father", "Smith", 180, true, grandpa, grandma);
        DeepPerson mother = new DeepPerson("Mother", "Jones", 165, false);

        // Children
        DeepPerson child1 = new DeepPerson("Child1", "Smith", 140, true, father, mother);
        DeepPerson child2 = new DeepPerson("Child2", "Smith", 130, false, father, mother);

        family.add(child1);
        family.add(child2);

        storageManager = EmbeddedStorage.start(family, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        DeepPerson child1Copy = objectCopier.copy(child1);

        assertNotSame(child1, child1Copy);
        assertNotSame(child1.getFather(), child1Copy.getFather());
        assertNotSame(child1.getMather(), child1Copy.getMather());
        assertNotSame(child1.getFather().getFather(), child1Copy.getFather().getFather());

        // Verify structure is preserved
        assertEquals(child1.getFather().getFirstName(), child1Copy.getFather().getFirstName());
        assertEquals(child1.getFather().getFather().getFirstName(),
                     child1Copy.getFather().getFather().getFirstName());
    }

    @Test
    public void deepCopyModifyMultipleLevelsTest() {
        List<DeepPerson> people = new ArrayList<>();

        DeepPerson grandpa = new DeepPerson("OldName", "Grandfather", 170, true);
        DeepPerson father = new DeepPerson("Dad", "Father", 180, true, grandpa, null);
        DeepPerson son = new DeepPerson("Son", "Junior", 160, true, father, null);

        people.add(son);

        storageManager = EmbeddedStorage.start(people, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        DeepPerson sonCopy = objectCopier.copy(son);

        // Modify at multiple levels
        sonCopy.setFirstName("ModifiedSon");
        sonCopy.getFather().setFirstName("ModifiedDad");
        sonCopy.getFather().getFather().setFirstName("ModifiedGrandpa");

        // Original should be unchanged
        assertEquals("Junior", son.getFirstName());
        assertEquals("Father", son.getFather().getFirstName());
        assertEquals("Grandfather", son.getFather().getFather().getFirstName());

        // Copy should be modified
        assertEquals("ModifiedSon", sonCopy.getFirstName());
        assertEquals("ModifiedDad", sonCopy.getFather().getFirstName());
        assertEquals("ModifiedGrandpa", sonCopy.getFather().getFather().getFirstName());
    }

    @Test
    public void deepCopyListOfPeopleTest() {
        List<DeepPerson> originalList = new ArrayList<>();

        originalList.add(new DeepPerson("Person1", "First", 180, true));
        originalList.add(new DeepPerson("Person2", "Second", 170, false));
        originalList.add(new DeepPerson("Person3", "Third", 165, true));

        storageManager = EmbeddedStorage.start(originalList, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        List<DeepPerson> copiedList = objectCopier.copy(originalList);

        assertNotSame(originalList, copiedList);
        assertEquals(originalList.size(), copiedList.size());

        for (int i = 0; i < originalList.size(); i++) {
            assertNotSame(originalList.get(i), copiedList.get(i));
            assertEquals(originalList.get(i).getFirstName(), copiedList.get(i).getFirstName());
        }

        // Modify copy
        copiedList.get(0).setFirstName("Modified");
        copiedList.add(new DeepPerson("New", "Person", 175, true));

        assertEquals("First", originalList.get(0).getFirstName());
        assertEquals(3, originalList.size());
    }

    @Test
    public void deepCopyWithAllPrimitiveTypesTest() {
        List<ComplexData> dataList = new ArrayList<>();

        ComplexData data = new ComplexData();
        data.byteValue = (byte) 127;
        data.shortValue = (short) 32000;
        data.intValue = 1000000;
        data.longValue = 9999999999L;
        data.floatValue = 3.14f;
        data.doubleValue = 2.718281828;
        data.charValue = 'Z';
        data.booleanValue = true;
        data.stringValue = "TestString";

        dataList.add(data);

        storageManager = EmbeddedStorage.start(dataList, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        ComplexData dataCopy = objectCopier.copy(data);

        assertNotSame(data, dataCopy);
        assertEquals(data.byteValue, dataCopy.byteValue);
        assertEquals(data.shortValue, dataCopy.shortValue);
        assertEquals(data.intValue, dataCopy.intValue);
        assertEquals(data.longValue, dataCopy.longValue);
        assertEquals(data.floatValue, dataCopy.floatValue);
        assertEquals(data.doubleValue, dataCopy.doubleValue);
        assertEquals(data.charValue, dataCopy.charValue);
        assertEquals(data.booleanValue, dataCopy.booleanValue);
        assertEquals(data.stringValue, dataCopy.stringValue);

        // Modify copy
        dataCopy.intValue = 999;
        dataCopy.stringValue = "Modified";

        assertEquals(1000000, data.intValue);
        assertEquals("TestString", data.stringValue);
    }

    @Test
    public void deepCopyWithCollectionsTest() {
        List<PersonWithCollections> people = new ArrayList<>();

        PersonWithCollections person = new PersonWithCollections("John", "Doe");
        person.addHobby("Reading");
        person.addHobby("Swimming");
        person.addAttribute("height", "180cm");
        person.addAttribute("weight", "75kg");
        person.addFriend(new DeepPerson("Friend1", "One", 175, true));
        person.addFriend(new DeepPerson("Friend2", "Two", 170, false));

        people.add(person);

        storageManager = EmbeddedStorage.start(people, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        PersonWithCollections personCopy = objectCopier.copy(person);

        assertNotSame(person, personCopy);
        assertNotSame(person.hobbies, personCopy.hobbies);
        assertNotSame(person.attributes, personCopy.attributes);
        assertNotSame(person.friends, personCopy.friends);

        assertEquals(person.hobbies.size(), personCopy.hobbies.size());
        assertTrue(personCopy.hobbies.contains("Reading"));

        // Modify copy's collections
        personCopy.addHobby("Coding");
        personCopy.attributes.put("height", "185cm");
        personCopy.friends.get(0).setFirstName("ModifiedFriend");

        assertEquals(2, person.hobbies.size());
        assertEquals(3, personCopy.hobbies.size());
        assertEquals("180cm", person.attributes.get("height"));
        assertEquals("One", person.friends.get(0).getFirstName());
    }

    @Test
    public void deepCopyArrayTest() {
        List<DataWithArray> dataList = new ArrayList<>();

        DataWithArray data = new DataWithArray();
        data.numbers = new int[]{1, 2, 3, 4, 5};
        data.people = new DeepPerson[]{
            new DeepPerson("Array1", "Person1", 180, true),
            new DeepPerson("Array2", "Person2", 170, false)
        };

        dataList.add(data);

        storageManager = EmbeddedStorage.start(dataList, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        DataWithArray dataCopy = objectCopier.copy(data);

        assertNotSame(data, dataCopy);
        assertNotSame(data.numbers, dataCopy.numbers);
        assertNotSame(data.people, dataCopy.people);

        assertEquals(data.numbers.length, dataCopy.numbers.length);
        assertEquals(data.people.length, dataCopy.people.length);

        for (int i = 0; i < data.people.length; i++) {
            assertNotSame(data.people[i], dataCopy.people[i]);
            assertEquals(data.people[i].getFirstName(), dataCopy.people[i].getFirstName());
        }

        // Modify copy
        dataCopy.numbers[0] = 999;
        dataCopy.people[0].setFirstName("ModifiedArray");

        assertEquals(1, data.numbers[0]);
        assertEquals("Person1", data.people[0].getFirstName());
    }

    @Test
    public void deepCopySamePersonMultipleReferencesTest() {
        List<DeepPerson> people = new ArrayList<>();

        DeepPerson sharedParent = new DeepPerson("Shared", "Parent", 180, true);

        // Two children sharing the same parent object
        DeepPerson child1 = new DeepPerson("Child1", "One", 140, true, sharedParent, null);
        DeepPerson child2 = new DeepPerson("Child2", "Two", 130, false, sharedParent, null);

        people.add(child1);
        people.add(child2);

        storageManager = EmbeddedStorage.start(people, tempDir);
        ObjectCopier objectCopier = ObjectCopier.New();

        DeepPerson child1Copy = objectCopier.copy(child1);
        DeepPerson child2Copy = objectCopier.copy(child2);

        assertNotSame(child1, child1Copy);
        assertNotSame(child2, child2Copy);
        assertNotSame(child1.getFather(), child1Copy.getFather());
        assertNotSame(child2.getFather(), child2Copy.getFather());

        // Each copy should have its own parent copy
        assertNotSame(child1Copy.getFather(), child2Copy.getFather());
    }

    @AfterEach
    public void afterTest() {
        if (storageManager != null) {
            storageManager.shutdown();
        }
    }


    static class DeepPerson {
        private String SecondName;
        private String FirstName;
        private Integer high;
        private Boolean man;
        private DeepPerson father;
        private DeepPerson mather;


        public DeepPerson(String secondName, String firstName, Integer high, Boolean man) {
            SecondName = secondName;
            FirstName = firstName;
            this.high = high;
            this.man = man;
        }

        public DeepPerson(String secondName, String firstName, Integer high, Boolean man, DeepPerson father, DeepPerson mather) {
            SecondName = secondName;
            FirstName = firstName;
            this.high = high;
            this.man = man;
            this.father = father;
            this.mather = mather;
        }

        public String getSecondName() {
            return SecondName;
        }

        public void setSecondName(String secondName) {
            SecondName = secondName;
        }

        public String getFirstName() {
            return FirstName;
        }

        public void setFirstName(String firstName) {
            FirstName = firstName;
        }

        public Integer getHigh() {
            return high;
        }

        public void setHigh(Integer high) {
            this.high = high;
        }

        public Boolean getMan() {
            return man;
        }

        public void setMan(Boolean man) {
            this.man = man;
        }

        public DeepPerson getFather() {
            return father;
        }

        public void setFather(DeepPerson father) {
            this.father = father;
        }

        public DeepPerson getMather() {
            return mather;
        }

        public void setMather(DeepPerson mather) {
            this.mather = mather;
        }

        @Override
        public String toString() {
            return "DeepPerson{" +
                    "SecondName='" + SecondName + '\'' +
                    ", FirstName='" + FirstName + '\'' +
                    ", high=" + high +
                    ", man=" + man +
                    ", father=" + father +
                    ", mather=" + mather +
                    '}';
        }
    }

    static class ComplexData {
        byte byteValue;
        short shortValue;
        int intValue;
        long longValue;
        float floatValue;
        double doubleValue;
        char charValue;
        boolean booleanValue;
        String stringValue;
    }

    static class PersonWithCollections {
        String firstName;
        String lastName;
        List<String> hobbies;
        Map<String, String> attributes;
        List<DeepPerson> friends;

        public PersonWithCollections(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.hobbies = new ArrayList<>();
            this.attributes = new HashMap<>();
            this.friends = new ArrayList<>();
        }

        public void addHobby(String hobby) {
            hobbies.add(hobby);
        }

        public void addAttribute(String key, String value) {
            attributes.put(key, value);
        }

        public void addFriend(DeepPerson friend) {
            friends.add(friend);
        }
    }

    static class DataWithArray {
        int[] numbers;
        DeepPerson[] people;
    }

}
