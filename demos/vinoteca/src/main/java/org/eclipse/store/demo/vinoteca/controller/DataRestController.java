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

/**
 * REST controller exposing data-management operations at {@code /api/v1/data}.
 * <p>
 * Provides on-demand access to the synthetic data generator and to top-level dataset metrics —
 * the same operations powering the "Data Generator" view of the Vaadin UI.
 */
@RestController
@RequestMapping("/api/v1/data")
public class DataRestController
{
	private final DataGeneratorService dataGeneratorService;

	/**
	 * @param dataGeneratorService the underlying data generator service
	 */
	public DataRestController(final DataGeneratorService dataGeneratorService)
	{
		this.dataGeneratorService = dataGeneratorService;
	}

	/**
	 * {@code POST /api/v1/data/generate} — append a fresh batch of generated data to the
	 * persistent graph.
	 *
	 * @param count the target number of wines to add (default {@code 50})
	 * @return aggregate counts of the entire persisted graph after the generation
	 */
	@PostMapping("/generate")
	public DataMetrics generate(@RequestParam(defaultValue = "50") final int count)
	{
		return this.dataGeneratorService.generate(count);
	}

	/**
	 * {@code GET /api/v1/data/metrics} — top-level entity counts of the persisted graph.
	 *
	 * @return aggregate dataset metrics
	 */
	@GetMapping("/metrics")
	public DataMetrics metrics()
	{
		return this.dataGeneratorService.getMetrics();
	}
}
