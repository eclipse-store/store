package org.eclipse.store.gigamap.codegen.test;

/*-
 * #%L
 * EclipseStore GigaMap Codegen
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

import org.eclipse.store.gigamap.annotations.Index;

/**
 * Record entity: the index annotations sit on the components; the generated metamodel must read each
 * value through the record's component accessor (the private backing field is not reachable).
 */
public record Article(
	@Index String title,
	@Index int views
)
{
	// record
}
