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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.apache.hc.core5.http.ParseException;
import org.eclipse.store.storage.restadapter.types.ViewerRootDescription;
import org.junit.jupiter.api.Test;

class RootTest extends AbstractIntegrationTest {

    @Test
    void obtainRootTest() throws IOException, ParseException
	{
        final String url = "http://localhost:4567/store-data/root";

        final ViewerRootDescription rootDescription = getAs(url, ViewerRootDescription.class);

        assertEquals("ROOT", rootDescription.getName());

        final long oid = lookupObjectId(storage.root());
        assertEquals(oid, rootDescription.getObjectId());

    }

}
