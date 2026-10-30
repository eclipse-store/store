package test.microstream.afs.azure.storage;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - azure
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

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;

public class AzureContainer {

    public static GenericContainer container;

    static {
        container = new GenericContainer(DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:latest"))
                .withExposedPorts(10000, 10001)
                .withCommand("azurite", "--blobHost", "0.0.0.0", "--queueHost", "0.0.0.0", "--skipApiVersionCheck");
        container.start();
    }

    public static BlobServiceClient createClient() {
        final String accountName = "devstoreaccount1";
        final String accountKey = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

        final BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                .endpoint("http://" + container.getHost() + ":" + container.getFirstMappedPort() + "/devstoreaccount1")
                .credential(new StorageSharedKeyCredential(accountName, accountKey))
                .buildClient();
        return serviceClient;
    }


}
