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
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class BasicLuceneTest
{

    @TempDir
    Path lucenePath;

    @TempDir
    Path storagePath;

    @Test
    void queryWithLimitTest() throws QueryNodeException
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                DirectoryCreator.ByteBuffers(),
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();
        try (LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext))) {

            gigaMap.add(new Article("Title_1", "SameText"));
            gigaMap.add(new Article("Title_2", "SameText"));
            gigaMap.add(new Article("Title_3", "SameText"));
            gigaMap.add(new Article("Title_4", "SameText"));
            gigaMap.add(new Article("Title_5", "SameText"));
            gigaMap.add(new Article("Title_6", "SameText"));
            gigaMap.add(new Article("Title_7", "SameText"));
            gigaMap.add(new Article("Title_8", "SameText"));
            gigaMap.add(new Article("Title_9", "SameText"));
            gigaMap.add(new Article("Title_10", "SameText"));
            gigaMap.add(new Article("Title_11", "SameText"));
            gigaMap.add(new Article("Title_12", "SameText"));
            gigaMap.add(new Article("Title_13", "SameText"));
            gigaMap.add(new Article("Title_14", "SameText"));
            gigaMap.add(new Article("Title_15", "SameText"));


            List<Article> result = new ArrayList<>();
            luceneIndex.query("content:SameText", 5, (id, entity, score) -> result.add(entity));
            assertEquals(5, result.size());
            assertEquals("Title_1", result.get(0).getTitle());

            result.clear();


            String queryText = "content:SameText";
            final StandardQueryParser queryParser = new StandardQueryParser(AnalyzerCreator.Standard().createAnalyzer());
            final Query query = queryParser.parse(queryText, "_id_");
            luceneIndex.query(query, 5, (id, entity, score) -> result.add(entity));
            assertEquals(5, result.size());
            assertEquals("Title_1", result.get(0).getTitle());
        }

    }

    @Test
    void internalAddAll()
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                DirectoryCreator.ByteBuffers(),
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();
        try (LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext))) {

            List<Article> articles = new ArrayList<>();
            articles.add(new Article("Title_1", "This is a first longer content text."));
            articles.add(new Article("Title_2", "This is a second longer Text"));
            articles.add(new Article("Title_3", "This is a third longer Text"));

            gigaMap.addAll(articles);

            List<Article> result = new ArrayList<>();
            luceneIndex.query("title:Title_2", (id, entity, score) -> result.add(entity));

            assertEquals(1, result.size());
            assertEquals("Title_2", result.get(0).getTitle());
        }
    }

    @Test
    void internalRemoveTest()
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                DirectoryCreator.ByteBuffers(),
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();
        try (LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext))) {

            fillData(gigaMap);

            gigaMap.removeById(0);

            List<Article> result = new ArrayList<>();
            luceneIndex.query("title:Title_1", (id, entity, score) -> result.add(entity));

            assertEquals(0, result.size());
        }

    }

    @Test
    void parentMapTest()
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                lucenePath,
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();
        try (LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext))) {

            assertSame(gigaMap, luceneIndex.parentMap());
        }
    }

    @Test
    void luceBasicContext_withPath() throws QueryNodeException
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                lucenePath,
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();
        try (LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext))) {

            fillData(gigaMap);

            List<Article> result = new ArrayList<>();
            luceneIndex.query("title:Title_1", (id, entity, score) -> result.add(entity));

            assertEquals(1, result.size());
            assertEquals("Title_1", result.get(0).getTitle());
        }

    }

    @Test
    void removeAllTest()
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                DirectoryCreator.ByteBuffers(),
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();
        try (LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext))) {

            fillData(gigaMap);

            gigaMap.removeAll();

            List<Article> result = new ArrayList<>();
            luceneIndex.query("title:Title_1", (id, entity, score) -> result.add(entity));

            assertEquals(0, result.size());
        }
    }

    @Test
    void luceBasicWithQuery() throws QueryNodeException
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                DirectoryCreator.ByteBuffers(),
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();
        try (LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext))) {

            fillData(gigaMap);

            List<Article> result = new ArrayList<>();
            String queryText = "title:Title_1";
            final StandardQueryParser queryParser = new StandardQueryParser(AnalyzerCreator.Standard().createAnalyzer());
            final Query query = queryParser.parse(queryText, "_id_");
            luceneIndex.query(query, (id, entity, score) -> result.add(entity));

            assertEquals(1, result.size());
            assertEquals("Title_1", result.get(0).getTitle());
        }
    }

    @Test
    void luceneBasicTest_MMapCreator()
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                DirectoryCreator.MMap(lucenePath),
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();
        LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext));

        fillData(gigaMap);

        List<Article> result = new ArrayList<>();
        luceneIndex.query("title:Title_1", (id, entity, score) -> result.add(entity));

        assertEquals(1, result.size());
        assertEquals("Title_1", result.get(0).getTitle());

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        luceneIndex.close();

        LuceneIndex<Article> luceneIndex2 = null;
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            GigaMap<Article> gigaMap2 = (GigaMap<Article>) storageManager.root();
            luceneIndex2 = gigaMap2.index().get(LuceneIndex.class);

            List<Article> result2 = new ArrayList<>();
            luceneIndex2.query("title:Title_1", (id, entity, score) -> result2.add(entity));

            assertEquals(1, result2.size());
            assertEquals("Title_1", result2.get(0).getTitle());
        } finally {
            if (luceneIndex2 != null) {
                luceneIndex2.close();
            }
        }

    }

    @Test
    void LuceneBasicTest()
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                DirectoryCreator.ByteBuffers(),
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();
        try (LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext))) {

            fillData(gigaMap);

            List<Article> result = new ArrayList<>();
            luceneIndex.query("title:Title_1", (id, entity, score) -> result.add(entity));

            assertEquals(1, result.size());
            assertEquals("Title_1", result.get(0).getTitle());


            result.clear();
            luceneIndex.query("content:\"This is a second \"", (id, entity, score) -> result.add(entity));

            assertEquals(1, result.size());
            assertEquals("Title_2", result.get(0).getTitle());
        }

    }

    @Test
//    @Disabled("https://github.com/microstream-one/gigamap/issues/135")
    void storeLuceneIndexTest()
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                DirectoryCreator.MMap(lucenePath),
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();
        LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext));

        fillData(gigaMap);

        List<Article> result = new ArrayList<>();
        luceneIndex.query("title:Title_1", (id, entity, score) -> result.add(entity));

        assertEquals(1, result.size());
        assertEquals("Title_1", result.get(0).getTitle());

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        luceneIndex.close();

        LuceneIndex<Article> luceneIndex2 = null;
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            GigaMap<Article> gigaMap2 = (GigaMap<Article>) storageManager.root();
            luceneIndex2 = gigaMap2.index().get(LuceneIndex.class);

            List<Article> result2 = new ArrayList<>();
            luceneIndex2.query("title:Title_1", (id, entity, score) -> result2.add(entity));

            assertEquals(1, result2.size());
            assertEquals("Title_1", result2.get(0).getTitle());
        } finally {
            if (luceneIndex2 != null) {
                luceneIndex2.close();
            }
        }
    }

    @Test
    void emptyQueryTest()
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                DirectoryCreator.ByteBuffers(),
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();
        try (LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext))) {

            List<Article> result = new ArrayList<>();
            luceneIndex.query("title:Title_1", (id, entity, score) -> result.add(entity));

            assertEquals(0, result.size());
        }
    }

    @Test
    void query_withQueryTest() throws ParseException
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                DirectoryCreator.ByteBuffers(),
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();

        try (LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext))) {

            fillData(gigaMap);
            //create lucene query
            Query query = new TermQuery(new Term("title", "title_1"));

            List<Article> result = luceneIndex.query(query);

            assertEquals(1, result.size());
        }
    }

    @Test
    void query_withQuery_MaxResults_Test() throws ParseException
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                DirectoryCreator.ByteBuffers(),
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();

        try (LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext))) {

            gigaMap.add(new Article("Title_1", "This is a first longer content text."));
            gigaMap.add(new Article("Title_1", "This is a second longer Text"));
            gigaMap.add(new Article("Title_1", "This is a third longer Text"));
            //create lucene query
            Query query = new TermQuery(new Term("title", "title_1"));

            List<Article> result = luceneIndex.query(query, 2);

            assertEquals(2, result.size());
        }
    }

    @Test
    void query_queryText() throws ParseException
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                DirectoryCreator.ByteBuffers(),
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();

        try (LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext))) {

            gigaMap.add(new Article("Title_1", "This is a first longer content text."));
            gigaMap.add(new Article("Title_1", "This is a second longer Text"));
            gigaMap.add(new Article("Title_1", "This is a third longer Text"));
            //create lucene query

            List<Article> result = luceneIndex.query("title:title_1");

            assertEquals(3, result.size());
        }
    }

    @Test
    void query_queryText_MaxResult() throws ParseException
    {
        ArticleDocumentPopulator documentPopulator = new ArticleDocumentPopulator();

        LuceneContext<Article> luceneContext = LuceneContext.New(
                DirectoryCreator.ByteBuffers(),
                documentPopulator
        );

        GigaMap<Article> gigaMap = GigaMap.New();

        try (LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext))) {

            gigaMap.add(new Article("Title_1", "This is a first longer content text."));
            gigaMap.add(new Article("Title_1", "This is a second longer Text"));
            gigaMap.add(new Article("Title_1", "This is a third longer Text"));
            //create lucene query

            List<Article> result = luceneIndex.query("title:title_1", 2);

            assertEquals(2, result.size());
        }
    }

    private void fillData(GigaMap<Article> gigaMap)
    {
        gigaMap.add(new Article("Title_1", "This is a first longer content text."));
        gigaMap.add(new Article("Title_2", "This is a second longer Text"));
        gigaMap.add(new Article("Title_3", "This is a third longer Text"));
    }


    private static class ArticleDocumentPopulator extends DocumentPopulator<Article>
    {

        @Override
        public void populate(Document document, Article entity)
        {
            document.add(createTextField("title", entity.getTitle()));
            document.add(createTextField("content", entity.getContent()));
        }
    }

    private static class Article
    {
        private final String title;
        private final String content;

        public Article(final String title, final String content)
        {
            super();
            this.title = title;
            this.content = content;
        }

        public String getTitle()
        {
            return this.title;
        }

        public String getContent()
        {
            return this.content;
        }

        @Override
        public String toString()
        {
            return "Article [title=" + this.title + ", content=" + this.content + "]";
        }
    }
}
