package test.eclipse.store.restservice.javalin;

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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.store.storage.restservice.types.StorageRestService;
import org.eclipse.store.storage.restservice.types.StorageRestServiceResolver;
import org.junit.jupiter.api.Test;


class RestServiceResolverTest extends AbstractIntegrationTest {

    @Test
    void getFirst() {
        final StorageRestService restService = StorageRestServiceResolver.resolve(storage);
        assertNotNull(restService);
    }

}
