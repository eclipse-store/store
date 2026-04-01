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

import org.apache.lucene.document.Document;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.gigamap.lucene.DocumentPopulator;

public class WineDocumentPopulator extends DocumentPopulator<Wine>
{
	@Override
	public void populate(final Document document, final Wine wine)
	{
		document.add(createTextField("name", wine.getName()));
		document.add(createTextField("tastingNotes", wine.getTastingNotes() != null ? wine.getTastingNotes() : ""));
		document.add(createTextField("aroma", wine.getAroma() != null ? wine.getAroma() : ""));
		document.add(createTextField("foodPairing", wine.getFoodPairing() != null ? wine.getFoodPairing() : ""));
		document.add(createStringField("type", wine.getType().name()));
		document.add(createStringField("grapeVariety", wine.getGrapeVariety().name()));
		document.add(createStringField("region", wine.getWinery().getRegion()));
		document.add(createStringField("country", wine.getWinery().getCountry()));
	}
}
