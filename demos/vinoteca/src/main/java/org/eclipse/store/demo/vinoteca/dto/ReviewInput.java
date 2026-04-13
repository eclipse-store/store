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
 * Inbound DTO used by the REST and GraphQL APIs to attach a
 * {@link org.eclipse.store.demo.vinoteca.model.Review Review} to a wine.
 * <p>
 * The {@link #customerIndex()} addresses the reviewer by position in
 * {@link org.eclipse.store.demo.vinoteca.model.DataRoot#getCustomers()}.
 *
 * @param customerIndex the index of the reviewing customer in the customers list
 * @param rating        the rating (typically 0.0 – 5.0)
 * @param text          the optional free-form review text
 */
public record ReviewInput(
	int    customerIndex,
	double rating,
	String text
)
{
}
