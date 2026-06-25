package test.eclipse.store.monitoring;

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

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.Set;

import org.eclipse.serializer.monitoring.MonitoringManager;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.monitoring.ObjectRegistryMonitorMBean;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.types.StorageLiveFileProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.library.TypeRegister;

public class MonitoringBaseTest
{

    @TempDir
    Path location;

    /**
     * This test prove, that some of the monitoring values are still there.
     *
     * @throws Exception
     */
    @Test
    void basicTest() throws Exception
    {
        TypeRegister root = new TypeRegister();
        root.fillSampleDate();
        final NioFileSystem fileSystem = NioFileSystem.New();

        String managerName = getNanoTime() + "StorageXXX";

        StorageConfiguration configuration = StorageConfiguration.Builder()
                .setStorageFileProvider(StorageLiveFileProvider.New(fileSystem.ensureDirectoryPath(location.toString())))
                .createConfiguration();

        EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(configuration)
                .setStorageMonitorManager(MonitoringManager.PlatformDependent(managerName));


        try (EmbeddedStorageManager storageManager = foundation.start(root)) {

            MBeanServer server = ManagementFactory.getPlatformMBeanServer();

            ObjectName name = new ObjectName("org.eclipse.store:*,name=ObjectRegistry");
            Set<ObjectName> names = server.queryNames(name, null);
            Assertions.assertNotEquals(0, names.size());

            ObjectRegistryMonitorMBean proxy = JMX.newMBeanProxy(server, new ObjectName("org.eclipse.store:storage="+managerName+",name=ObjectRegistry"), ObjectRegistryMonitorMBean.class);
            Assertions.assertNotEquals(0, proxy.getSize());

            Object invokeResult = server.getAttribute(new ObjectName("org.eclipse.store:storage="+managerName+",name=ObjectRegistry"), "Size");
            Assertions.assertNotEquals(0, invokeResult);
        }

    }

    synchronized long getNanoTime() {
    	return System.nanoTime();
    }
}
