package org.eclipse.store.gigamap.lucene;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
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
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.ScoredSearchResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a search maps its hits to the right entity ids when it reads them from the doc values of the
 * id field: the hits come in score order, the doc values are read in document order, segment by segment.
 */
public class LuceneEntityIdReadTest
{
	private static final String ID_FIELD = "_id_";

	private static class Note
	{
		final String text;

		Note(final String text)
		{
			this.text = text;
		}
	}

	private static class NotePopulator extends DocumentPopulator<Note>
	{
		@Override
		public void populate(final Document document, final Note entity)
		{
			document.add(createTextField("text", entity.text));
		}
	}

	// adds a field of the index's own id name, with a value smaller than every entity id
	private static class ForeignIdPopulator extends DocumentPopulator<Note>
	{
		@Override
		public void populate(final Document document, final Note entity)
		{
			document.add(createTextField("text", entity.text));
			document.add(new LongField(ID_FIELD, -1L, Store.YES));
		}
	}


	@Test
	void hitsInScoreOrderCarryTheIdsOfTheirEntities() throws IOException
	{
		final GigaMap<Note> map = GigaMap.New();
		try(final LuceneIndex<Note> index = map.index().register(
			LuceneIndex.Category(LuceneContext.New(DirectoryCreator.ByteBuffers(), new NotePopulator()))
		))
		{
			// the more often the word occurs, the better the score, so the later documents rank before the
			// earlier ones, and a search in between leaves the documents in several segments.
			final int count = 40;
			for(int i = 0; i < count; i++)
			{
				map.add(new Note("filler " + "lucene ".repeat(i + 1) + "note" + i));
				if(i % 7 == 6)
				{
					assertEquals(i + 1, index.search("text:lucene", count).size());
				}
			}

			final List<ScoredSearchResult.Entry<Note>> entries = index.search("text:lucene", count).toList();
			assertEquals(count, entries.size());
			float previousScore = Float.MAX_VALUE;
			for(final ScoredSearchResult.Entry<Note> entry : entries)
			{
				assertTrue(entry.score() <= previousScore, "hits are in score order");
				previousScore = entry.score();
				assertTrue(
					entry.entity().text.endsWith("note" + entry.entityId()),
					"entity " + entry.entityId() + " is the one of its hit"
				);
			}
			// in document order the ids would count up from 0: the hits are not in that order.
			assertTrue(entries.get(0).entityId() > entries.get(count - 1).entityId());
			assertEquals(
				count,
				entries.stream().map(ScoredSearchResult.Entry::entityId).distinct().count(),
				"every entity is found once"
			);

			// the premise of the read: the id field the index writes has doc values in every segment.
			final DirectoryReader reader = ((LuceneIndex.Default<Note>)index).reader;
			assertTrue(reader.leaves().size() > 1, "the documents lie in several segments");
			for(final LeafReaderContext leaf : reader.leaves())
			{
				assertNotNull(
					leaf.reader().getSortedNumericDocValues(ID_FIELD),
					"the id field has doc values in every segment"
				);
			}

			final List<Long> acceptedIds = new ArrayList<>();
			index.query("text:lucene", count, (id, entity, score) ->
			{
				assertTrue(entity.text.endsWith("note" + id));
				acceptedIds.add(id);
			});
			assertEquals(entries.stream().map(ScoredSearchResult.Entry::entityId).toList(), acceptedIds);
		}
	}

	@Test
	void aPopulatorsOwnIdFieldDoesNotChangeTheIdsOfTheHits()
	{
		final GigaMap<Note> map = GigaMap.New();
		try(final LuceneIndex<Note> index = map.index().register(
			LuceneIndex.Category(LuceneContext.New(DirectoryCreator.ByteBuffers(), new ForeignIdPopulator()))
		))
		{
			// the entity ids count up from 0
			final long first  = map.add(new Note("lucene note0"));
			final long second = map.add(new Note("lucene note1"));

			final List<Long> found = new ArrayList<>();
			for(final ScoredSearchResult.Entry<Note> entry : index.search("text:lucene", 10))
			{
				assertNotNull(entry.entity(), "hit " + entry.entityId() + " has its entity");
				assertTrue(entry.entity().text.endsWith("note" + entry.entityId()));
				found.add(entry.entityId());
			}
			found.sort(null);
			assertEquals(List.of(first, second), found);

			final List<Long> accepted = new ArrayList<>();
			index.query("text:lucene", 10, (id, entity, score) ->
			{
				assertTrue(entity.text.endsWith("note" + id));
				accepted.add(id);
			});
			accepted.sort(null);
			assertEquals(List.of(first, second), accepted);
		}
	}

	@Test
	void idsComeFromDocValuesAndFromStoredFieldsWhereASegmentHasNone() throws IOException
	{
		try(Directory directory = new ByteBuffersDirectory())
		{
			try(IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig()))
			{
				// ids as doc values only, not stored: the stored fields could not answer for them.
				for(long id = 100; id < 105; id++)
				{
					final Document document = new Document();
					document.add(new LongField(ID_FIELD, id, Store.NO));
					writer.addDocument(document);
				}
			}

			try(DirectoryReader reader = DirectoryReader.open(directory))
			{
				final IndexSearcher searcher  = new IndexSearcher(reader);
				final ScoreDoc[]    scoreDocs =
				{
					new ScoreDoc(3, 5f), new ScoreDoc(0, 4f), new ScoreDoc(4, 3f), new ScoreDoc(1, 2f)
				};
				assertArrayEquals(
					new long[]{103L, 100L, 104L, 101L},
					LuceneIndex.Default.entityIds(searcher, scoreDocs)
				);
				assertArrayEquals(new long[0], LuceneIndex.Default.entityIds(searcher, new ScoreDoc[0]));
			}
		}

		// a document with two values for the id field: the id is the first stored one, not the smallest doc value.
		try(Directory directory = new ByteBuffersDirectory())
		{
			try(IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig()))
			{
				final Document document = new Document();
				document.add(new LongField(ID_FIELD, 7L, Store.YES));
				document.add(new LongField(ID_FIELD, 3L, Store.YES));
				writer.addDocument(document);
			}

			try(DirectoryReader reader = DirectoryReader.open(directory))
			{
				assertArrayEquals(
					new long[]{7L},
					LuceneIndex.Default.entityIds(new IndexSearcher(reader), new ScoreDoc[]{new ScoreDoc(0, 1f)})
				);
			}
		}

		// an index whose id field has no doc values: the ids come from the stored field.
		try(Directory directory = new ByteBuffersDirectory())
		{
			try(IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig()))
			{
				for(long id = 200; id < 204; id++)
				{
					final Document document = new Document();
					document.add(new LongPoint(ID_FIELD, id));
					document.add(new StoredField(ID_FIELD, id));
					writer.addDocument(document);
				}
			}

			try(DirectoryReader reader = DirectoryReader.open(directory))
			{
				final IndexSearcher searcher = new IndexSearcher(reader);
				final TopDocs       topDocs  = searcher.search(LongPoint.newRangeQuery(ID_FIELD, 201L, 203L), 10);
				assertEquals(3, topDocs.scoreDocs.length);
				final StoredFields storedFields = searcher.storedFields();
				final long[]       expected     = new long[3];
				for(int i = 0; i < expected.length; i++)
				{
					expected[i] = storedFields.document(topDocs.scoreDocs[i].doc)
						.getField(ID_FIELD).numericValue().longValue();
				}
				assertArrayEquals(expected, LuceneIndex.Default.entityIds(searcher, topDocs.scoreDocs));
				assertArrayEquals(
					new long[]{202L, 200L},
					LuceneIndex.Default.entityIds(searcher, new ScoreDoc[]{new ScoreDoc(2, 1f), new ScoreDoc(0, 1f)})
				);
			}
		}
	}
}
