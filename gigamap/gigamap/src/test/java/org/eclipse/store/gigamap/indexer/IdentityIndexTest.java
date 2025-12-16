package org.eclipse.store.gigamap.indexer;

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

import org.eclipse.serializer.util.X;
import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Consumer;

import net.datafaker.Faker;

public class IdentityIndexTest
{
    Faker faker = new Faker();


    @Test
    void registerAddIndexTest()
    {
        final GigaMap<IdentityAddress> gigaMap = GigaMap.New();

        final BitmapIndices<IdentityAddress> indices = gigaMap.index().bitmap();
        final IdentityAddressIndexer streetIndexer = new IdentityAddressIndexer();
        indices.add(streetIndexer);

        indices.setIdentityIndices(X.Enum(streetIndexer));

        final long add = gigaMap.add(new IdentityAddress("streetOrig", "city1", "zip1"));

        indices.internalAdd(0, new IdentityAddress("street1", "city1", "zip1"));

        gigaMap.query(streetIndexer.is("street1")).execute((Consumer<? super IdentityAddress>) a -> {
            Assertions.assertEquals("streetOrig", a.getStreet());
        });

    }

    @Test
    void registerRemoveIndexTest()
    {
        final GigaMap<IdentityAddress> gigaMap = GigaMap.New();

        final BitmapIndices<IdentityAddress> indices = gigaMap.index().bitmap();
        final IdentityAddressIndexer streetIndexer = new IdentityAddressIndexer();
        indices.add(streetIndexer);

        indices.setIdentityIndices(X.Enum(streetIndexer));

        final long add = gigaMap.add(new IdentityAddress("streetOrig", "city1", "zip1"));

        indices.internalRemove(0, new IdentityAddress("streetOrig", "city1", "zip1"));

        gigaMap.query(streetIndexer.is("street1")).execute((Consumer<? super IdentityAddress>) Assertions::assertNull);

    }

    @Test
    void indexEntityTest()
    {
        final GigaMap<IdentityAddress> gigaMap = GigaMap.New();

        final BitmapIndices<IdentityAddress> indices = gigaMap.index().bitmap();
        final IdentityAddressIndexer streetIndexer = new IdentityAddressIndexer();
        indices.add(streetIndexer);

        indices.setIdentityIndices(X.Enum(streetIndexer));

        final IdentityAddress identityAddress = new IdentityAddress("streetOrig", "city1", "zip1");

        final long add = gigaMap.add(identityAddress);

        final String s = streetIndexer.index(identityAddress);
        Assertions.assertEquals("streetOrig", s);

    }


    //address generator
    private IdentityAddress generateAddress()
    {
        return new IdentityAddress(this.faker.address().streetAddress(), this.faker.address().city(), this.faker.address().zipCode());
    }

    class IdentityAddressIndexer extends IndexerString.Abstract<IdentityAddress>
    {
    	@Override
    	protected String getString(final IdentityAddress entity)
        {
            return entity.getStreet();
        }
    }

    static class IdentityAddress
    {
        private String street;
        private String city;
        private String zip;
        private UUID id;

        public IdentityAddress(final String street, final String city, final String zip)
        {
            this.street = street;
            this.city = city;
            this.zip = zip;
            this.id = UUID.randomUUID();
            //this.id = UUID.nameUUIDFromBytes("something".getBytes());

        }

        public String getStreet()
        {
            return this.street;
        }

        public void setStreet(final String street)
        {
            this.street = street;
        }

        public String getCity()
        {
            return this.city;
        }

        public void setCity(final String city)
        {
            this.city = city;
        }

        public String getZip()
        {
            return this.zip;
        }

        public void setZip(final String zip)
        {
            this.zip = zip;
        }

        public UUID getId()
        {
            return this.id;
        }

        public void setId(final UUID id)
        {
            this.id = id;
        }

        @Override
        public String toString()
        {
            return "IdentityAddress{" +
                    "street='" + this.street + '\'' +
                    ", city='" + this.city + '\'' +
                    ", zip='" + this.zip + '\'' +
                    ", id=" + this.id +
                    '}';
        }
    }

}

