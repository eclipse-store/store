package org.eclipse.store.gigamap.restart;

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

import static org.eclipse.store.gigamap.restart.AllTypesPojoIndices.*;

public class AllTypeIndicesMapCreator
{

    public GigaMap<AllTypesPojo> generateMap(int size)
    {
        GigaMap<AllTypesPojo> map = GigaMap.New();

        map.index().bitmap().addAll(stringFieldIndex, intFieldIndex, longFieldIndex, doubleFieldIndex, booleanFieldIndex,
                charFieldIndex, byteFieldIndex, shortFieldIndex, floatFieldIndex, longObjectFieldIndex, doubleFieldObjectIndex,
                booleanFieldObjectIndex, charFieldObjectIndex, byteFieldObjectIndex, shortFieldObjectIndex, floatFieldObjectIndex,
                bigIntegerFieldIndex, localDateFieldIndex, localDateTimeFieldIndex, localTimeFieldIndex, uuidFieldIndex, yearMonthFieldIndex);
        map.index().bitmap().setIdentityIndices(uuidFieldIndex);

        for (int i = 0; i < size; i++) {
            AllTypesPojo pojo = new AllTypesPojo();
            pojo.generateRandomData();
            map.add(pojo);
        }
        return map;
    }
}
