package test.eclipse.store.restservice.javalin.getobject;

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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import org.apache.hc.core5.http.ParseException;
import org.eclipse.store.storage.restadapter.types.ViewerObjectDescription;
import org.junit.jupiter.api.Test;

import test.eclipse.store.restservice.javalin.AbstractIntegrationTest;
import test.eclipse.store.restservice.javalin.UrlBuilder;

public class RootObjectTest extends AbstractIntegrationTest
{
	@Test
	public void RootElementContentTest() throws IOException, ParseException
	{
		final long oid =1000000000000000001L;
	    final String url = new UrlBuilder(oid).build();
	    final ViewerObjectDescription objectDescription = requestObjectByUrl(url);

	    assertEquals(0, Long.parseLong(objectDescription.getLength()));
	    assertEquals(25, Long.parseLong(objectDescription.getVariableLength()[0]));
	    assertEquals(25, Long.parseLong(objectDescription.getVariableLength()[1]));
	    assertNull(objectDescription.getReferences());
	}

}
