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

/**
 * Populates a Lucene {@link Document} with searchable fields derived from a {@link Wine}.
 * <p>
 * Drives the Lucene full-text index registered on the wines GigaMap. The {@code name},
 * {@code tastingNotes}, {@code aroma} and {@code foodPairing} fields are tokenised
 * ({@link DocumentPopulator#createTextField text fields}) so that the full-text and
 * similarity-search screens can match arbitrary keywords; {@code type}, {@code grapeVariety},
 * {@code region} and {@code country} are stored as untokenised
 * ({@link DocumentPopulator#createStringField string fields}, lowercased) for exact-term
 * filtering inside Lucene queries.
 */
public class WineDocumentPopulator extends DocumentPopulator<Wine>
{
	/**
	 * Adds Lucene fields for a single wine. Null-valued tasting notes, aroma and food-pairing are
	 * mapped to the empty string so the Lucene field is always present.
	 *
	 * @param document the Lucene document to add fields to
	 * @param wine     the source wine
	 */
	@Override
	public void populate(final Document document, final Wine wine)
	{
		document.add(createTextField("name", wine.getName()));
		document.add(createTextField("tastingNotes", wine.getTastingNotes() != null ? wine.getTastingNotes() : ""));
		document.add(createTextField("aroma", wine.getAroma() != null ? wine.getAroma() : ""));
		document.add(createTextField("foodPairing", wine.getFoodPairing() != null ? wine.getFoodPairing() : ""));
		document.add(createStringField("type", wine.getType().name().toLowerCase()));
		document.add(createStringField("grapeVariety", wine.getGrapeVariety().name().toLowerCase()));
		document.add(createStringField("region", wine.getWinery().getRegion().toLowerCase()));
		document.add(createStringField("country", wine.getWinery().getCountry().toLowerCase()));
	}
}
