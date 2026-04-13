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

/**
 * Inbound DTO used by the REST and GraphQL APIs to create a
 * {@link org.eclipse.store.demo.vinoteca.model.Customer Customer}.
 *
 * @param firstName the given name
 * @param lastName  the family name
 * @param email     the email address
 * @param city      the city (may be {@code null})
 * @param country   the country (may be {@code null})
 */
public record CustomerInput(
	String firstName,
	String lastName,
	String email,
	String city,
	String country
)
{
}
