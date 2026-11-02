package test.eclipse.store.restservice.javalin;

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

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.serializer.collections.EqHashTable;


public class TestGraph {
    public String stringMember;
    public String stringNullRef;
    public String stringEmpty;
    public BigDecimal bigDezEmpty;
    public BigDecimal bigDezValue;
    public BigDecimal bigDezValue2;
    public HashMap<Long, String> hashMap;
    public ArrayList<Integer> arrayList;

    public int anInt = 42;
    public Integer anIntegerConstant = 34;
    public Integer anInteger = 1000;

    public int[] intArray = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    public String[] stringArray = {"A", "String", "array", "with", "data"};

    public EqHashTable<Integer, String> eqHashTable;

    public Thread.State threadStateEnum = Thread.State.TERMINATED;
    public char aChar = 'X';

    public URL url;

    public TestGraph()
    {
        this.stringMember = "This is a string";
        this.stringNullRef = null;
        this.stringEmpty = "";
        this.bigDezValue = new BigDecimal("121314151617181920212223242526");
        this.bigDezValue2 = new BigDecimal("111111111111111111111111.44444444444444444444");

        this.hashMap = new HashMap<>(3);
        this.hashMap.put(10101L, "hashmap entry 1");
        this.hashMap.put(11111L, "hashmap entry 2");
        this.hashMap.put(10001L, "hashmap entry 3");

        this.arrayList = new ArrayList<>(5);
        this.arrayList.add(1);
        this.arrayList.add(2);
        this.arrayList.add(3);
        this.arrayList.add(4);
        this.arrayList.add(5);

        this.eqHashTable = EqHashTable.New();
        this.eqHashTable.put(1, "text A");
        this.eqHashTable.put(2, "text B");
        this.eqHashTable.put(3, "text C");
        this.eqHashTable.put(4, "text D");

        try
        {
			this.url = new URL("http", "localhost", 2636, "/somerwhere");
		}
        catch (final MalformedURLException e)
        {
        	throw new RuntimeException(e.getMessage());
		}

    }
}
