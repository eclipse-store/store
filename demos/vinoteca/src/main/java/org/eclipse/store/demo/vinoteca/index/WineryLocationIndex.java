package org.eclipse.store.demo.vinoteca.index;

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

import org.eclipse.store.demo.vinoteca.model.Winery;
import org.eclipse.store.gigamap.types.SpatialIndexer;

public class WineryLocationIndex extends SpatialIndexer.Abstract<Winery>
{
	@Override
	protected Double getLatitude(final Winery winery)
	{
		return winery.getLatitude();
	}

	@Override
	protected Double getLongitude(final Winery winery)
	{
		return winery.getLongitude();
	}
}
