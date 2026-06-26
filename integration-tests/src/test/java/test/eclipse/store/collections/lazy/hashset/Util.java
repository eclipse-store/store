package test.eclipse.store.collections.lazy.hashset;

/*-
 * #%L
 * MicroStream Base
 * %%
 * Copyright (C) 2019 - 2022 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.collections.lazy.LazyHashSet;

import net.datafaker.Faker;

public class Util
{

    static Faker faker = new Faker();


    public static LazyHashSet<String> generateLazyHashSet(int segmentSize, int count)
    {
        LazyHashSet<String> lazyHashSet = new LazyHashSet<>(segmentSize);
        for (int i = 0; i < count; i++) {
            lazyHashSet.add(faker.lorem()
                    .sentence());
        }
        return lazyHashSet;
    }

}
