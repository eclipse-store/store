package test.microstream.afs.jimfs;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - kafka
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

import java.util.Properties;

import org.apache.kafka.clients.CommonClientConfigs;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.eclipse.store.afs.kafka.types.KafkaConnector;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;


public class Container {

    private static final String KAFKA_SERVER_DEFAULT = "localhost:9093";
    public static KafkaContainer kafkaContainer;
    private static AFileSystem aFileSystem;
    private static KafkaConnector kafkaConnector;

    static {
        // CI runs the pinned image; -Dafs.kafka.image=confluentinc/cp-kafka:7.6.1 for hosts without amd64 emulation (arm64)
        kafkaContainer = new KafkaContainer(DockerImageName.parse(System.getProperty("afs.kafka.image", "confluentinc/cp-kafka:6.2.1"))
                .asCompatibleSubstituteFor("confluentinc/cp-kafka"));
        kafkaContainer.start();
    }

    public static AFileSystem resolveFilesystem() {
        if (aFileSystem == null) {
            kafkaConnector = createKafkaConnector();
            aFileSystem = BlobStoreFileSystem.New(kafkaConnector);
        }
        return aFileSystem;
    }

    public static KafkaConnector createKafkaConnector() {
        final Properties properties = new Properties();
        properties.setProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, findKafkaServer());
        return KafkaConnector.Caching(properties);
    }

    public static KafkaConnector getKafkaConnector() {
        return kafkaConnector;
    }

    private static String findKafkaServer() {
        return kafkaContainer.getBootstrapServers();
    }
}
