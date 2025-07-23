package org.eclipse.store.gigamap.lucene;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.apache.lucene.document.Document;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BothIndexerTest
{

    @Test
    void bothIndexesTest()
    {
        NormalBothIndexer normalIndexer = new NormalBothIndexer();
        LuceneBothDocumentPopulator luceneDocumentPopulator = new LuceneBothDocumentPopulator();


        LuceneContext<BothEntity> luceneContext = LuceneContext.New(
                DirectoryCreator.ByteBuffers(),
                luceneDocumentPopulator
        );

        GigaMap<BothEntity> gigaMap = GigaMap.New();
        try (LuceneIndex<BothEntity> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext)) ) {

            gigaMap.index().bitmap().add(normalIndexer);

            BothEntity both1 = new BothEntity("lucene_1", "normal");
            BothEntity both2 = new BothEntity("lucene2", "normal2");
            gigaMap.addAll(both1, both2);

            List<BothEntity> result = new ArrayList<>();
            luceneIndex.query("lucene_index:lucene_1", (id, entity, score) -> result.add(entity));

            assertEquals(1, result.size());
            assertEquals("lucene_1", result.get(0).getLucene());

            List<BothEntity> result2 = gigaMap.query(normalIndexer.is("normal")).toList();
            assertEquals(1, result2.size());
            assertEquals("normal", result2.get(0).getNormalIndex());
        }
    }

    private static class LuceneBothDocumentPopulator extends DocumentPopulator<BothEntity>
    {

        @Override
        public void populate(Document document, BothEntity entity)
        {
            document.add(createTextField("lucene_index", entity.getLucene()));
        }
    }


    private static class NormalBothIndexer extends IndexerString.Abstract<BothEntity>
    {
    	@Override
	   	protected String getString(BothEntity entity)
	   	{
            return entity.normalIndex;
        }
    }

    private static class BothEntity
    {
        private final String luceneIndex;
        private final String normalIndex;

        public BothEntity(String lucene, String normalIndex)
        {
            super();
            this.luceneIndex = lucene;
            this.normalIndex = normalIndex;
        }

        public String getLucene()
        {
            return luceneIndex;
        }

        public String getNormalIndex()
        {
            return normalIndex;
        }

        @Override
        public String toString()
        {
            return "BothEntity{" +
                    "lucene='" + luceneIndex + '\'' +
                    ", normalIndex='" + normalIndex + '\'' +
                    '}';
        }
    }
}
