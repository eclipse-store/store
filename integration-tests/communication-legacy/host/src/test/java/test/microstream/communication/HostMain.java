package test.microstream.communication;

/*-
 * #%L
 * EclipseStore Integration Tests - Communication Legacy - Host
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

/**
 * Entry point of the host JVM: runs one {@link HostLegacyTest} method, which serves exactly one client
 * exchange and returns once the client is done. Usage: {@code HostMain <methodName>} with {@code -Dmsport=<port>}.
 */
public final class HostMain
{
	public static void main(final String[] args) throws Exception
	{
		final HostLegacyTest host = new HostLegacyTest();
		try
		{
			HostLegacyTest.class.getMethod(args[0]).invoke(host);
		}
		finally
		{
			host.stopServer();
		}
		System.exit(0);
	}
}
