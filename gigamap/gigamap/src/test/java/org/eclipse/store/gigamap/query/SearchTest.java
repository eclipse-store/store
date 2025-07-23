package org.eclipse.store.gigamap.query;

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

import com.github.javafaker.Faker;
import org.eclipse.store.gigamap.experimental.Address;
import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class SearchTest
{

    @Test
    void gigaSimpleSearchTest()
    {
        final Faker faker = new Faker();
        final GigaMap<Address> gigaMap = GigaMap.New();

        final AddressStreetIndexer streetIndexer = new AddressStreetIndexer();
        gigaMap.index().bitmap().add(streetIndexer);

        for (int i = 0; i < 100; i++) {
            gigaMap.add(new Address(faker.address().streetAddress(), faker.address().city(), faker.address().zipCode()));
        }

        gigaMap.update(gigaMap.get(10),address -> address.setStreet("test"));

        final List<Address> foundAddress = new ArrayList<>();
        gigaMap.query(streetIndexer.is("test")).execute((Consumer<? super Address>) foundAddress::add);

        Assertions.assertEquals(1, foundAddress.size());
    }

    @Test
    void gigaSearchTest()
    {
        final AtomicInteger counterResult = new AtomicInteger();
        final Faker faker = new Faker();
        final GigaMap<Address> gigaMap = GigaMap.New();

        final AddressStreetIndexer streetIndexer = new AddressStreetIndexer();
        gigaMap.index().bitmap().add(streetIndexer);

        for (int i = 0; i < 10; i++) {
            gigaMap.add(new Address(faker.address().streetAddress(), faker.address().city(), faker.address().zipCode()));
        }

        for (int i = 0; i < 100; i++) {
            gigaMap.add(new Address("test", "test", "test"));
        }

        counterResult.set(0);

        gigaMap.query(streetIndexer.is("test")).execute((Consumer<? super Address>) a -> {
            counterResult.getAndIncrement();
        });

        Assertions.assertEquals(100, counterResult.get());

    }


    @Test
    void gigaSearchAllTest()
    {
        final AtomicInteger counterResult = new AtomicInteger();
        final Faker faker = new Faker();
        final GigaMap<Address> gigaMap = GigaMap.New();

        final AddressStreetIndexer streetIndexer = new AddressStreetIndexer();
        gigaMap.index().bitmap().add(streetIndexer);

        for (int i = 0; i < 10; i++) {
            gigaMap.add(new Address(faker.address().streetAddress(), faker.address().city(), faker.address().zipCode()));
        }

        for (int i = 0; i < 100; i++) {
            gigaMap.add(new Address("test", "test", "test"));
        }

        counterResult.set(0);

        gigaMap.query(streetIndexer.not("something")).execute((Consumer<? super Address>) a -> {
            counterResult.getAndIncrement();
        });

        Assertions.assertEquals(110, counterResult.get());

    }

    @Test
    void gigaSearchXTest()
    {
        final GigaMap<Address> gigaMap = GigaMap.New();

        final BitmapIndices<Address> indices = gigaMap.index().bitmap();
        final AddressStreetIndexer streetIndexer = new AddressStreetIndexer();
        final AddressComplexStreetIndexer complexStreetIndexer = new AddressComplexStreetIndexer();
        indices.add(streetIndexer);
        indices.add(complexStreetIndexer);

        for (int i = 0; i < 100; i++) {
            gigaMap.add(new Address("test", "test", "test"));
        }

        final Address testAddress = new Address("test", "test", "test");

        Assertions.assertTrue(streetIndexer.test(testAddress, "test"));

    }

    private static class AddressStreetIndexer extends IndexerString.Abstract<Address>
    {
        @Override
        protected String getString(final Address entity)
        {
            return entity.street();
        }
    }

    private static class AddressComplexStreetIndexer extends AddressStreetIndexer
    {
        @Override
        protected String getString(final Address entity)
        {
            return entity.street();
        }
    }
}
