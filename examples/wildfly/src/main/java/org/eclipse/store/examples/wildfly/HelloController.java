package org.eclipse.store.examples.wildfly;

/*-
 * #%L
 * microstream-examples-wildfly
 * %%
 * Copyright (C) 2019 - 2021 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */


import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/hello")
@Singleton
public class HelloController
{
	@GET
	public String sayHello()
	{
		return "Hello World";
	}
}
