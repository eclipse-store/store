package test.microstream.coms.ltm;

/*-
 * #%L
 * EclipseStore Integration Tests - Communication Legacy - Client
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


import org.eclipse.serializer.communication.binarydynamic.ComBinaryDynamic;
import org.eclipse.serializer.communication.types.ComChannel;
import org.eclipse.serializer.communication.types.ComClient;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import test.microstream.legacy.basic.data.AttPerson;
import test.microstream.legacy.basic.data.BooleanLegacy;
import test.microstream.legacy.basic.data.ByteLegacy;
import test.microstream.legacy.basic.data.Person;
import test.microstream.legacy.basic.data.ReferencePerson;
import test.microstream.legacy.fail.Car;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LegacyTypeMappingTest extends AbstractCommunicationTest {

    private final Logger logger = LoggerFactory.getLogger(LegacyTypeMappingTest.class);
    private ComChannel channel;

    private Process process;

    @AfterEach
    public void afterEach() throws InterruptedException {
        process.destroy();
        process.waitFor();
    }

    @Test
    @Timeout(value = 40, unit = SECONDS)
    public void personLegacyTest() throws IOException, InterruptedException {
        int port = findFreePort();
        process = startServerProccess("HostLegacyTest#sendPersonTest", port);
        //waitForPortToBeAvailable(port);
        channel = openChannel(port);
        Person person = (Person) getObjectFromServer();
        assertNotNull(person);
        channel.send(person);
        String s = (String) getObjectFromServer();
        System.out.println(s);
    }

    @Test
    @Timeout(value = 40, unit = SECONDS)
    public void attrPersonTest(@TempDir Path path) throws IOException, InterruptedException {
        int port = findFreePort();
        process = startServerProccess("HostLegacyTest#attrPersonTest", port);
        channel = openChannel(port);
        AttPerson person = (AttPerson) getObjectFromServer();
        assertNotNull(person);
        channel.send(person);
        String s = (String) getObjectFromServer();
        System.out.println(s);

        EmbeddedStorageManager storage = EmbeddedStorage.start(person, path);
        storage.shutdown();
    }

    @Test
    @Timeout(value = 40, unit = SECONDS)
    public void booleanLegacyTest() throws IOException, InterruptedException {
        int port = findFreePort();
        process = startServerProccess("HostLegacyTest#booleanLegacyTest", port);
        channel = openChannel(port);
        BooleanLegacy dto = (BooleanLegacy) getObjectFromServer();
        assertNotNull(dto);
        channel.send(dto);
        String s = (String) getObjectFromServer();
        System.out.println(s);
    }

    @Test
    @Timeout(value = 40, unit = SECONDS)
    public void byteLegacyTest() throws InterruptedException, IOException {
        int port = findFreePort();
        process = startServerProccess("HostLegacyTest#byteLegacyTest", port);
        channel = openChannel(port);

        ByteLegacy dto = (ByteLegacy) getObjectFromServer();
        assertNotNull(dto);
        channel.send(dto);
        String s = (String) getObjectFromServer();
        System.out.println(s);
    }

    @Test
    @Timeout(value = 40, unit = SECONDS)
    public void referencePersonTest() throws IOException, InterruptedException {
        int port = findFreePort();
        process = startServerProccess("HostLegacyTest#referencePersonTest", port);
        channel = openChannel(port);

        ReferencePerson dto = (ReferencePerson) getObjectFromServer();
        assertNotNull(dto);
        channel.send(dto);
        String s = (String) getObjectFromServer();
        System.out.println(s);
    }

    @Test
    @Timeout(value = 40, unit = SECONDS)
    public void missingClassTest() throws IOException, InterruptedException {
        int port = findFreePort();
        process = startServerProccess("HostLegacyTest#missingClassTest", port);
        channel = openChannel(port);
        Car dto = (Car) getObjectFromServer();
        assertNotNull(dto);
    }

    private ComChannel openChannel(Integer port) {
        ComClient<?> client = ComBinaryDynamic.Client(port);
        return client.connect(20, Duration.ofMillis(1000));

    }

    private Object getObjectFromServer() {

        return channel.receive();
    }

}
