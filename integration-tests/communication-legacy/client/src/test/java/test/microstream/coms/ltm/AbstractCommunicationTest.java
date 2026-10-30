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

import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public abstract class AbstractCommunicationTest {

    private static final AtomicInteger port = new AtomicInteger(49_500);

    protected static synchronized Integer findPort() {
        return port.addAndGet(3);
    }


    /**
     * Starts the host module's test method in a second JVM: the host is compiled against the legacy-a class
     * shapes, this module against legacy-b, so the two must never share a class path. The host class path is
     * written by the host module's build (maven-dependency-plugin, host-classpath.txt).
     */
    protected Process startServerProccess(String testMethod, int port, String... params) throws IOException {
        Path hostDir = Path.of(System.getProperty("legacy.host.dir", "../host"));
        String classPath = Files.readString(hostDir.resolve("target/host-classpath.txt")).trim()
                + File.pathSeparator + hostDir.resolve("target/test-classes");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String method = testMethod.substring(testMethod.indexOf('#') + 1);

        String[] basisParam = new String[]{java, String.format("-Dmsport=%d", port), "-cp", classPath,
                "test.microstream.communication.HostMain", method};
        String[] processBuilderParam = Stream.concat(Arrays.stream(basisParam), Arrays.stream(params)).toArray(String[]::new);

        return new ProcessBuilder(processBuilderParam).inheritIO().start();
    }

    public static int findFreePort() {
        Integer port = null;
        try (ServerSocket serverSocket = new ServerSocket(0)) {

            port = serverSocket.getLocalPort();
            // setReuseAddress(false) is required only on OSX,
            // otherwise the code will not work correctly on that platform
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        Assertions.assertNotNull(port);
        return port;
    }


    protected void waitForPortToBeAvailable(int port) {
        boolean scanning = true;
        int counter = 0;
        while (scanning) {
            try {
                SocketChannel.open(new InetSocketAddress(port));
                scanning = false;
            } catch (Exception e) {
                counter++;
                if (counter > 50) {
                    throw new RuntimeException("Cannot connect to web server on port:." + port);
                }
                System.out.println("Connect failed, waiting and trying again: " + counter);
                try {
                    Thread.sleep(500);//2 seconds
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }


    public boolean isMac() {
        return  System.getProperty("os.name").contains("Mac");
    }
}
