package test.eclipse.store.various;

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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.serializer.concurrency.LockScope;
import org.eclipse.serializer.concurrency.LockedExecutor;
import org.junit.jupiter.api.Test;

public class ConcurrencyHelpersTest extends LockScope
{

    @Test
    void concurrencyHelpersTest() throws InterruptedException
    {
        List<Long> list = new ArrayList<>();
        int threadCount = 50;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(
                    () -> {
                        write(
                                () -> {
                                    for (int j = 0; j < 1000; j++) {
                                        list.add(System.currentTimeMillis());
                                    }
                                }
                        );
                        read(
                                () -> {
                                    List<String> collect = list.parallelStream()
                                            .map(l -> l.toString())
                                            .collect(Collectors.toList());
                                }
                        );
                        write(
                                () -> {
                                    for (int j = 0; j < 1000; j++) {
                                        list.add(System.currentTimeMillis());
                                    }
                                }
                        );
                    }
            );

            threads[i] = thread;
            thread.start();

        }


        for (int i = 0; i < threadCount; i++) {
            threads[i].join();
        }
    }


    @Test
    void concurrencyHelpers_direct_Test() throws InterruptedException
    {
        final LockedExecutor executor = LockedExecutor.New();

        List<Long> list = new ArrayList<>();
        int threadCount = 50;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(
                    () -> {
                        executor.write(
                                () -> {
                                    for (int j = 0; j < 1000; j++) {
                                        list.add(System.currentTimeMillis());
                                    }
                                }
                        );
                        executor.read(
                                () -> {
                                    List<String> collect = list.parallelStream()
                                            .map(l -> l.toString())
                                            .collect(Collectors.toList());
                                }
                        );
                        executor.write(
                                () -> {
                                    for (int j = 0; j < 1000; j++) {
                                        list.add(System.currentTimeMillis());
                                    }
                                }
                        );
                    }
            );

            threads[i] = thread;
            thread.start();

        }


        for (int i = 0; i < threadCount; i++) {
            threads[i].join();
        }
    }

}
