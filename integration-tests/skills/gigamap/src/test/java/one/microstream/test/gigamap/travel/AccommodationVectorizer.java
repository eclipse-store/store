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

import org.eclipse.store.gigamap.jvector.Vectorizer;

public class AccommodationVectorizer extends Vectorizer<Accommodation>
{
    public static final int DIMENSION = 8;

    @Override
    public float[] vectorize(Accommodation a)
    {
        return a.featureVector();
    }

    @Override
    public boolean isEmbedded() { return true; }
}
