package test.eclipse.store.collections.lazy.hashmap;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.*;

import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.eclipse.serializer.util.logging.Logging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

// 271124223868563

public class LazyHashMapRandomTest
{

    private final static Logger logger = Logging.getLogger(LazyHashMapRandomTest.class);

    public Random rnd = new Random(42);
    private final int numAction = 7;
    private final int cycles = 10000;

    LazyHashMap<String, String> lazyMap = new LazyHashMap<>(10);
    Map<String, String> refMap = new HashMap<>();

    List<String> protocoll = new ArrayList<>();

    public static void main(final String[] args)
    {

        LazyHashMap<String, String> l = new LazyHashMap<>(10);

        l.put("Key_" + 1402217696, "Value " + 1402217696);

        final Iterator<String> lazyIter = l.keySet().iterator();
        while (lazyIter.hasNext()) {
            final String key = lazyIter.next();
            if (key.contains("21")) {
                lazyIter.remove();
            }
        }

        final Iterator<String> lazyIter2 = l.keySet().iterator();
        while (lazyIter2.hasNext()) {
            final String key = lazyIter2.next();
            if (key.contains("21")) {
                lazyIter2.remove();
            }
        }

        //l.replace(null, null);

        l.remove("Key_" + 1081360197);
    }


    @BeforeEach
    void setupTest()
    {
        this.rnd = new Random(System.nanoTime());
        this.protocoll = new ArrayList<>();

        this.lazyMap = new LazyHashMap<>(10);
        this.refMap = new HashMap<>();
    }


    @RepeatedTest(value = 1)
    @Disabled("This test takes to long to run.")
    void repeatedRandomActionTest()
    {
        this.RandomActionTest();
    }


    @Test
    @Disabled("This test takes to long to run.")
    void RandomActionTest()
    {
        for (int i = 0; i < this.cycles; i++) {
            this.randomAction();
        }

        this.compare();

        while (!this.lazyMap.isEmpty()) {
            final String key = this.getRndKey();
            this.refMap.remove(key);
            this.lazyMap.remove(key);
        }

        assertEquals(this.refMap.size(), this.lazyMap.size(), "size should be 0");
    }

    public void compare()
    {
        assertEquals(this.refMap.size(), this.lazyMap.size());

        for (final String key : this.refMap.keySet()) {
            final String ref = this.refMap.get(key);
            final String lazy = this.lazyMap.get(key);
            assertEquals(ref, lazy, "Values for key " + key + " don't match!");
        }
    }

    public void randomAction()
    {
        switch (this.rnd.nextInt(this.numAction)) {
            case 0:
                this.protocoll.add("actionPut");
                this.actionPut();
                break;
            case 1:
                this.protocoll.add("actionRemove");
                this.actionRemove();
                break;
            case 2:
                this.protocoll.add("actionReplace");
                this.actionReplace();
                break;
            case 3:
                this.protocoll.add("actionReplace");
                this.actionReplaceEquals();
                break;
            case 4:
                this.protocoll.add("actionKeyIterateRemove");
                this.actionKeyIterateRemove();
                break;
            case 5:
                this.protocoll.add("actionPut");
                this.actionPut();
                break;
            case 6:
                this.protocoll.add("actionPut");
                this.actionPut();
                break;
            default:
                break;
        }
    }

    public void actionKeyIterateRemove()
    {

        logger.debug("actionKeyIterateRemove");

        final Iterator<String> refIter = this.refMap.keySet().iterator();
        while (refIter.hasNext()) {
            final String key = refIter.next();
            if (key.contains("21")) {
                refIter.remove();
            }
        }

        final Iterator<String> lazyIter = this.lazyMap.keySet().iterator();
        while (lazyIter.hasNext()) {
            final String key = lazyIter.next();
            if (key.contains("21")) {
                lazyIter.remove();
            }
        }
    }

    public void actionReplace()
    {

        if (this.refMap.keySet().size() <= 0) return;

        final String key = this.getRndKey();
        logger.debug("replace {} ", key);

        final String lazy = this.lazyMap.replace(key, "replaced " + key);
        final String ref = this.refMap.replace(key, "replaced " + key);

        assertEquals(ref, lazy);
    }

    public void actionReplaceEquals()
    {

        if (this.refMap.keySet().size() <= 0) return;

        final String key = this.getRndKey();
        logger.debug("replace {} ", key);

        final String currentValue = this.refMap.get(key);

        final boolean lazy = this.lazyMap.replace(key, currentValue, "replaced " + key);
        final boolean ref = this.refMap.replace(key, currentValue, "replaced " + key);

        assertEquals(ref, lazy);
    }

    public String getRndKey()
    {
        final int r = this.rnd.nextInt(this.refMap.keySet().size());
        final Iterator<String> iter = this.refMap.keySet().iterator();
        for (int i = 0; i < r - 1; i++) {
            iter.next();
        }
        final String key = iter.next();
        return key;
    }

    public void actionRemove()
    {
        final int id = this.rnd.nextInt();

        logger.debug("remove {} ", id);

        final String lazy = this.lazyMap.remove("Key_" + id);
        final String ref = this.refMap.remove("Key_" + id);
        assertEquals(ref, lazy);
    }

    public void actionPut()
    {
        final int id = this.rnd.nextInt();

        logger.debug("put {} ", id);

        final String lazy = this.lazyMap.put("Key_" + id, "Value " + id);
        final String ref = this.refMap.put("Key_" + id, "Value " + id);
        assertEquals(ref, lazy);
    }


}
