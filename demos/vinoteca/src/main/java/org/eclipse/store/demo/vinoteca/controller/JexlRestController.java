package org.eclipse.store.demo.vinoteca.controller;

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

import org.eclipse.store.demo.vinoteca.dto.JexlRequest;
import org.eclipse.store.demo.vinoteca.dto.JexlResponse;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.demo.vinoteca.service.JexlService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jexl")
public class JexlRestController
{
	private final JexlService jexlService;

	public JexlRestController(final JexlService jexlService)
	{
		this.jexlService = jexlService;
	}

	@PostMapping("/filter-wines")
	public List<Wine> filterWines(@RequestBody final JexlRequest request)
	{
		return this.jexlService.filterWines(request.expression());
	}

	@PostMapping("/evaluate")
	public JexlResponse evaluate(@RequestBody final JexlRequest request)
	{
		final Object result = this.jexlService.evaluate(request.expression());
		return new JexlResponse(request.expression(), result);
	}
}
