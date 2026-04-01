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

import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.gigamap.jvector.Vectorizer;

public class WineVectorizer extends Vectorizer<Wine>
{
	private final EmbeddingModel embeddingModel;

	public WineVectorizer(final EmbeddingModel embeddingModel)
	{
		this.embeddingModel = embeddingModel;
	}

	@Override
	public float[] vectorize(final Wine wine)
	{
		return this.embeddingModel.embed(toText(wine)).content().vector();
	}

	@Override
	public List<float[]> vectorizeAll(final List<? extends Wine> entities)
	{
		return this.embeddingModel.embedAll(
				entities.stream().map(WineVectorizer::toText).map(TextSegment::from).toList()
			)
			.content()
			.stream()
			.map(Embedding::vector)
			.toList();
	}

	private static String toText(final Wine wine)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(wine.getName());
		if (wine.getGrapeVariety() != null)
		{
			sb.append(' ').append(wine.getGrapeVariety().name().replace('_', ' '));
		}
		if (wine.getTastingNotes() != null)
		{
			sb.append(' ').append(wine.getTastingNotes());
		}
		return sb.toString();
	}
}
