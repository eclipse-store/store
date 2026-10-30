package test.microstream.afs.googlecloud.firestore;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - googlecloud-firestore
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

import java.util.HashMap;
import java.util.Map;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import test.microstream.afs.blobstore.BlobStoreFileSystemTest;

public class FirestoreContainer {

    public static GenericContainer<?> container;

    static {
        // Public Firestore emulator from the Google Cloud CLI image (the "emulators" tag bundles it).
        container = new GenericContainer<>(DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators"))
                .withCommand("gcloud", "emulators", "firestore", "start", "--host-port=0.0.0.0:9090")
                .withExposedPorts(9090)
                .waitingFor(Wait.forLogMessage(".*Dev App Server is now running.*\\n", 1)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        container.start();
    }

    public static Firestore createFirestore() {
        // https://cloud.google.com/storage/docs/reference/libraries#setting_up_authentication

        final Map<String, String> env = new HashMap<>();
        env.put(
                "GOOGLE_APPLICATION_CREDENTIALS",
                "[file-path]"
        );
        BlobStoreFileSystemTest.setEnv(env);

        final FirestoreOptions firestoreOptions = FirestoreOptions.getDefaultInstance().toBuilder()
                //.setProjectId("firetest-280612")
                .setProjectId("microstream")
                .setCredentialsProvider(NoCredentials::getInstance)
                .setHost(container.getHost() + ":" + container.getFirstMappedPort())
                .build();

        final Firestore firestore = firestoreOptions.getService();
        return firestore;
    }


};
