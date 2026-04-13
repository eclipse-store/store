package org.eclipse.store.demo.vinoteca.config;

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

import java.util.Map;

import graphql.schema.DataFetcher;
import org.eclipse.store.demo.vinoteca.model.Order;
import org.eclipse.store.demo.vinoteca.model.OrderItem;
import org.eclipse.store.demo.vinoteca.model.Review;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * GraphQL runtime wiring for the Vinoteca schema.
 * <p>
 * The schema (see {@code src/main/resources/graphql/schema.graphqls}) exposes flattened scalar
 * fields for several derived attributes that have no direct getter on the underlying domain types:
 * for example {@code Wine.wineryName}, {@code Order.customerName}, {@code OrderItem.subtotal} or
 * {@code Wine.price} (which has to flatten {@link javax.money.MonetaryAmount MonetaryAmount} into
 * a plain {@code double}). This configuration registers the corresponding
 * {@link DataFetcher data fetchers} so that those schema fields can be resolved from the domain
 * objects without polluting the model with presentation-only accessors.
 */
@Configuration
public class GraphQlConfig
{
	/**
	 * Wires the GraphQL runtime with custom data fetchers for the {@code Wine}, {@code Order},
	 * {@code OrderItem} and {@code Review} types.
	 *
	 * @return a configurer that augments the auto-generated wiring with derived-field fetchers
	 */
	@Bean
	public RuntimeWiringConfigurer runtimeWiringConfigurer()
	{
		return wiringBuilder -> wiringBuilder
			.type("Wine", builder -> builder
				.dataFetcher("wineryName", env -> {
					final Wine wine = env.getSource();
					return wine.getWinery() != null ? wine.getWinery().getName() : null;
				})
				.dataFetcher("price", env -> {
					final Wine wine = env.getSource();
					return wine.getPrice() != null ? wine.getPrice().getNumber().doubleValue() : 0.0;
				})
				.dataFetcher("currency", env -> {
					final Wine wine = env.getSource();
					return wine.getPrice() != null ? wine.getPrice().getCurrency().getCurrencyCode() : "EUR";
				})
			)
			.type("Order", builder -> builder
				.dataFetcher("customerName", env -> {
					final Order order = env.getSource();
					return order.getCustomer() != null ? order.getCustomer().getFullName() : null;
				})
				.dataFetcher("orderDate", env -> {
					final Order order = env.getSource();
					return order.getOrderDate() != null ? order.getOrderDate().toString() : null;
				})
				.dataFetcher("total", env -> {
					final Order order = env.getSource();
					final var total = order.getTotal();
					return total != null ? total.getNumber().doubleValue() : null;
				})
			)
			.type("OrderItem", builder -> builder
				.dataFetcher("wineName", env -> {
					final OrderItem item = env.getSource();
					return item.getWine() != null ? item.getWine().getName() : null;
				})
				.dataFetcher("priceAtPurchase", env -> {
					final OrderItem item = env.getSource();
					return item.getPriceAtPurchase() != null ? item.getPriceAtPurchase().getNumber().doubleValue() : 0.0;
				})
				.dataFetcher("subtotal", env -> {
					final OrderItem item = env.getSource();
					return item.getSubtotal() != null ? item.getSubtotal().getNumber().doubleValue() : 0.0;
				})
			)
			.type("Review", builder -> builder
				.dataFetcher("customerName", env -> {
					final Review review = env.getSource();
					return review.getCustomer() != null ? review.getCustomer().getFullName() : null;
				})
				.dataFetcher("date", env -> {
					final Review review = env.getSource();
					return review.getDate() != null ? review.getDate().toString() : null;
				})
			);
	}
}
