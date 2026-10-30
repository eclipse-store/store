package test.microstream.communication;

/*-
 * #%L
 * EclipseStore Integration Tests - Communication Legacy - Host
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
import org.eclipse.serializer.communication.types.ComHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import test.microstream.legacy.basic.data.AttPerson;
import test.microstream.legacy.basic.data.BooleanLegacy;
import test.microstream.legacy.basic.data.ByteLegacy;
import test.microstream.legacy.basic.data.Person;
import test.microstream.legacy.basic.data.ReferencePerson;
import test.microstream.legacy.fail.Car;

import java.nio.ByteOrder;

import static java.util.concurrent.TimeUnit.SECONDS;

public class HostLegacyTest {

    private ComHost<?> host;
    private final String original = "Some String to transfer";

    @Test
    @Timeout(value = 40, unit = SECONDS)
    public void sendPersonTest() {
        System.out.println("SendPersonTest start");
        Person person = Person.createSample();
        startServer(person, Person.class);
    }

    @Test
    @Timeout(value = 40, unit = SECONDS)
    public void attrPersonTest() {
        AttPerson person = AttPerson.createSample();
        startServer(person, AttPerson.class);

    }

    @Test
    @Timeout(value = 40, unit = SECONDS)
    public void booleanLegacyTest() {
        BooleanLegacy booleanLegacy = BooleanLegacy.fillSample();
        startServer(booleanLegacy, BooleanLegacy.class);
    }

    @Test
    @Timeout(value = 40, unit = SECONDS)
    public void byteLegacyTest() {
        ByteLegacy dto = ByteLegacy.fillSample();
        startServer(dto, ByteLegacy.class);
    }

    @Test
    @Timeout(value = 40, unit = SECONDS)
    public void missingClassTest() {
        Car dto = new Car("big");
        int port = Integer.parseInt(System.getProperty("msport", "1234"));
        System.out.println("XXXX  readed port: " + port);

        host = ComBinaryDynamic.Foundation()
                .setHostByteOrder(ByteOrder.LITTLE_ENDIAN)
                .setPort(port)
                .setHostChannelAcceptor(hostChannel ->
                {
                    hostChannel.send(dto);
                    stopServer();

                })
                .createHost()
        ;
        host.run();
    }

    @Test
    @Timeout(value = 40, unit = SECONDS)
    public void referencePersonTest() {
        ReferencePerson person = ReferencePerson.createSample();
        startServer(person, ReferencePerson.class);
    }


    public void startServer(Object original, Class<?> type) {

        int port = Integer.parseInt(System.getProperty("msport", "1234"));
        System.out.println("XXXX  readed port: " + port);

        host = ComBinaryDynamic.Foundation()
                .setHostByteOrder(ByteOrder.LITTLE_ENDIAN)
                .setPort(port)
                .setHostChannelAcceptor(hostChannel ->
                {
                    hostChannel.send(original);

                    Object copy = hostChannel.receive();
                    System.out.println("Server Received :" + copy);
                    Assertions.assertInstanceOf(type, copy);
                    hostChannel.send("END");
                    stopServer();

                })
                .createHost()
        ;
        host.run();

    }


    @AfterEach
    public void stopServer() {
        try {
            host.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
