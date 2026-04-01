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

import org.eclipse.store.demo.vinoteca.dto.DataMetrics;
import org.eclipse.store.demo.vinoteca.service.DataGeneratorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data")
public class DataRestController
{
	private final DataGeneratorService dataGeneratorService;

	public DataRestController(final DataGeneratorService dataGeneratorService)
	{
		this.dataGeneratorService = dataGeneratorService;
	}

	@PostMapping("/generate")
	public DataMetrics generate(@RequestParam(defaultValue = "50") final int count)
	{
		return this.dataGeneratorService.generate(count);
	}

	@GetMapping("/metrics")
	public DataMetrics metrics()
	{
		return this.dataGeneratorService.getMetrics();
	}
}
