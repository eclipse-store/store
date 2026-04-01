package org.eclipse.store.demo.vinoteca.dto;

/*-
 * #%L
 * EclipseStore Demo Vinoteca
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

public record WineryInput(
	String name,
	String region,
	String country,
	double latitude,
	double longitude,
	String description,
	int    foundedYear
)
{
}
