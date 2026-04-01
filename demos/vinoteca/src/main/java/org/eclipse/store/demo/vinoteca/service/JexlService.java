package org.eclipse.store.demo.vinoteca.service;

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

import java.util.List;

import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.JexlScript;
import org.apache.commons.jexl3.MapContext;
import org.eclipse.store.demo.vinoteca.model.DataRoot;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.demo.vinoteca.model.WineType;
import org.springframework.stereotype.Service;

@Service
public class JexlService
{
	private final JexlEngine jexlEngine;
	private final DataRoot   dataRoot;

	public JexlService(final JexlEngine jexlEngine, final DataRoot dataRoot)
	{
		this.jexlEngine = jexlEngine;
		this.dataRoot   = dataRoot;
	}

	public List<Wine> filterWines(final String expression)
	{
		final JexlScript script = this.jexlEngine.createScript(expression, "wine");
		return this.dataRoot.getWines().query()
			.stream()
			.filter(wine -> {
				final JexlContext ctx = new MapContext();
				ctx.set("wine", wine);
				ctx.set("WineType", WineType.class);
				final Object result = script.execute(ctx);
				return Boolean.TRUE.equals(result);
			})
			.toList();
	}

	public Object evaluate(final String expression)
	{
		final JexlExpression expr = this.jexlEngine.createExpression(expression);
		final JexlContext ctx = new MapContext();
		ctx.set("wines", this.dataRoot.getWines().query().toList());
		ctx.set("wineries", this.dataRoot.getWineries().query().toList());
		ctx.set("customers", this.dataRoot.getCustomers());
		ctx.set("orders", this.dataRoot.getOrders());
		ctx.set("data", this.dataRoot);
		ctx.set("WineType", WineType.class);
		return expr.evaluate(ctx);
	}
}
