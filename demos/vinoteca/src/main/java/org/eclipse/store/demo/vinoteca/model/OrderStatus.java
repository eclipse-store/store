package org.eclipse.store.demo.vinoteca.model;

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
 * Lifecycle state of an {@link Order}.
 * <p>
 * Orders typically progress {@link #PENDING} → {@link #CONFIRMED} → {@link #SHIPPED} →
 * {@link #DELIVERED}; {@link #CANCELLED} is a terminal state that may be entered from any earlier
 * stage. The Vinoteca demo does not enforce these transitions strictly — they are simply the
 * conventional flow used by the data generator and the orders view.
 */
public enum OrderStatus
{
	/** The order has been placed but not yet acknowledged. */
	PENDING,
	/** The order has been confirmed and is awaiting shipment. */
	CONFIRMED,
	/** The order has shipped and is in transit. */
	SHIPPED,
	/** The order has been delivered to the customer. */
	DELIVERED,
	/** The order was cancelled — terminal state. */
	CANCELLED
}
