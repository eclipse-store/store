package org.eclipse.store.storage.restservice.sparkjava.exceptions;

/*-
 * #%L
 * EclipseStore Storage REST Service Sparkjava
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

public class InvalidRouteParametersException extends RuntimeException
{
	private static final String EXCEPTION_TEXT = "invalid url parameter ";

	public InvalidRouteParametersException(final String parameterName)
	{
		super(EXCEPTION_TEXT + parameterName);
	}

}
