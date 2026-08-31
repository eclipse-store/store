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
import org.eclipse.store.storage.restadapter.types.ViewerObjectDescription;
import org.junit.jupiter.api.Test;


class IntegrationTest_ModifyAndLoadTest extends AbstractIntegrationTest {

    @Test
    void modifyAndReLoad() throws IOException, ParseException
	{
        final ViewerObjectDescription objectDescriptionInitial = testRequestNoParams(testData.stringMember);
        assertEquals("This is a string", objectDescriptionInitial.getData()[0]);

        testData.stringMember = "This string contains some text";
        storage.store(testData);

        final ViewerObjectDescription objectDescriptionModified = testRequestNoParams(testData.stringMember);
        assertEquals("This string contains some text", objectDescriptionModified.getData()[0]);
    }

}
