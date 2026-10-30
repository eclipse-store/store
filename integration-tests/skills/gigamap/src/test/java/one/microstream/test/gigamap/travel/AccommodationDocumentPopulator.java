package one.microstream.test.gigamap.travel;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - gigamap
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
import org.eclipse.store.gigamap.lucene.DocumentPopulator;

public class AccommodationDocumentPopulator extends DocumentPopulator<Accommodation>
{
    @Override
    public void populate(Document doc, Accommodation a)
    {
        doc.add(createTextField("name",        a.name()));
        doc.add(createTextField("country",     a.country()));
        doc.add(createTextField("city",        a.city()));
        doc.add(createTextField("description", a.description()));
        doc.add(createTextField("type",        a.type().name()));
        doc.add(createTextField("amenities",   String.join(" ", a.amenities())));
    }
}
