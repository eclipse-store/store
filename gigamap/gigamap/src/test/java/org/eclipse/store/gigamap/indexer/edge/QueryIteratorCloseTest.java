package org.eclipse.store.gigamap.indexer.edge;

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

import org.eclipse.store.gigamap.types.GigaIterator;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class QueryIteratorCloseTest
{

    @Test
    void addAllAfterQueryTest()
    {
        final GigaMap<NamePerson> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(nameIndexer);

        final GigaMap<NamePerson> gigaMap2 = GigaMap.New();
        gigaMap2.index().bitmap().add(nameIndexer);

        gigaMap.add(new NamePerson("name1", 1));
        gigaMap2.add(new NamePerson("name1", 1));

        gigaMap.query(nameIndexer.is("name1")).forEach(person ->  {});
        gigaMap2.query(nameIndexer.is("name1")).forEach(person ->  {});


        final List<NamePerson> personList = new ArrayList<>();
        personList.add(new NamePerson("name1", 1));

        gigaMap.addAll(personList);

        assertEquals(2, gigaMap.query(nameIndexer.is("name1")).count());
    }


    @Test
    void addAllWithIndexAfterQuery()
    {
        final GigaMap<NamePerson> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(nameIndexer);

        final List<NamePerson> personList = new ArrayList<>();
        personList.add(new NamePerson("name1", 1));

        gigaMap.query(nameIndexer.is("name1")).forEach(person ->  {});

        gigaMap.addAll(personList);
    }

    @Test
    void removeWithIndexAfterQueryWithSession()
    {
        final GigaMap<NamePerson> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(nameIndexer);
        final NamePerson namePerson = new NamePerson("name1", 1);
        gigaMap.add(namePerson);

        try (Stream<NamePerson> name1 = gigaMap.query(nameIndexer.is("name1")).stream() )
        {
            name1.forEach(person ->  {});
        }

        gigaMap.remove(namePerson);

    }


    @Test
    void queryForEachClosesIteratorOnException()
    {
        final GigaMap<NamePerson> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(nameIndexer);
        final NamePerson person = new NamePerson("name1", 1);
        gigaMap.add(person);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try
            {
                gigaMap.query(nameIndexer.is("name1")).forEach(p -> {
                    throw new RuntimeException("boom");
                });
            }
            catch (final RuntimeException expected)
            {
                // swallow: the consumer threw on purpose
            }

            // Must NOT deadlock: forEach has to release the read-lock even though the consumer threw.
            gigaMap.update(person, p -> p.setName("changed"));
        });

        assertEquals(1, gigaMap.query(nameIndexer.is("changed")).count());
    }

    @Test
    void mapForEachClosesIteratorOnException()
    {
        final GigaMap<NamePerson> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(nameIndexer);
        final NamePerson person = new NamePerson("name1", 1);
        gigaMap.add(person);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try
            {
                gigaMap.forEach(p -> {
                    throw new RuntimeException("boom");
                });
            }
            catch (final RuntimeException expected)
            {
                // swallow: the consumer threw on purpose
            }

            // Must NOT deadlock: forEach has to release the read-lock even though the consumer threw.
            gigaMap.update(person, p -> p.setName("changed"));
        });

        assertEquals(1, gigaMap.query(nameIndexer.is("changed")).count());
    }


    @Test
    void nextPastEndReleasesLockOnThrow()
    {
        final GigaMap<NamePerson> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(nameIndexer);
        final NamePerson person = new NamePerson("name1", 1);
        gigaMap.add(person);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            // A matching query yields a real BitmapIterator holding a read-lock (an empty query
            // would return the always-safe GigaIterator.Empty and not exercise the guard).
            // Deliberately NOT closing the iterator: driving next() one past the end must release
            // the read-lock itself when it throws NoSuchElementException. A try-with-resources
            // would mask the leak.
            final GigaIterator<NamePerson> it = gigaMap.query(nameIndexer.is("name1")).iterator();
            it.hasNext();                                         // prepares the single match
            it.next();                                           // consumes it, clearing the buffer
            assertThrows(NoSuchElementException.class, it::next); // scrolls past end -> must self-close

            // Must NOT deadlock: next() has to release the read-lock on throw.
            gigaMap.update(person, p -> p.setName("changed"));
        });

        assertEquals(1, gigaMap.query(nameIndexer.is("changed")).count());
    }


    @Test
    void mutationWhileHoldingOwnIteratorThrows()
    {
        final GigaMap<NamePerson> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(nameIndexer);
        final NamePerson person = new NamePerson("name1", 1);
        gigaMap.add(person);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            // Open and hold a reader without exhausting/closing it.
            final GigaIterator<NamePerson> it = gigaMap.query(nameIndexer.is("name1")).iterator();
            it.hasNext();

            // Mutating the same GigaMap on the same thread that holds the reader would deadlock;
            // it must fail fast with IllegalStateException instead.
            assertThrows(IllegalStateException.class,
                () -> gigaMap.update(person, p -> p.setName("changed")));

            it.close();
        });
    }

    @Test
    void mutationFromOtherThreadStillWaits() throws InterruptedException
    {
        final GigaMap<NamePerson> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(nameIndexer);
        final NamePerson person = new NamePerson("name1", 1);
        gigaMap.add(person);

        final CountDownLatch readerOpened = new CountDownLatch(1);
        final CountDownLatch releaseReader = new CountDownLatch(1);
        final AtomicReference<Throwable> holderError = new AtomicReference<>();

        // A different thread holds an open reader, then releases it on signal.
        final Thread holder = new Thread(() -> {
            try (final GigaIterator<NamePerson> it = gigaMap.query(nameIndexer.is("name1")).iterator())
            {
                it.hasNext();
                readerOpened.countDown();
                releaseReader.await(5, TimeUnit.SECONDS);
            }
            catch (final Throwable t)
            {
                holderError.set(t);
            }
        }, "reader-holder");
        holder.start();

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            readerOpened.await();
            // The mutating thread does NOT own the reader, so it must wait (not throw). Release the
            // holder shortly after so the update can proceed once the foreign reader is closed.
            final Thread releaser = new Thread(releaseReader::countDown, "releaser");
            releaser.start();
            gigaMap.update(person, p -> p.setName("changed"));
        });

        holder.join();
        assertNull(holderError.get());
        assertEquals(1, gigaMap.query(nameIndexer.is("changed")).count());
    }


    static IndexerString<NamePerson> nameIndexer = new IndexerString.Abstract<>()
    {
        @Override
        protected String getString(final NamePerson entity)
        {
            return entity.name;
        }
    };

    static class NamePerson {
        private String name;
        private int age;

        public NamePerson(final String name, final int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return this.name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public int getAge() {
            return this.age;
        }

        public void setAge(final int age) {
            this.age = age;
        }
    }
}
