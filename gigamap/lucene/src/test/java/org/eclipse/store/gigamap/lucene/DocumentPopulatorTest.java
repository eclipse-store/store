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
import org.apache.lucene.document.*;
import org.apache.lucene.search.Query;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DocumentPopulatorTest
{

    @Test
    void populatorTest()
    {
        PopDocumentPopulator populator = new PopDocumentPopulator();

        LuceneContext<PopEntity> luceneContext = LuceneContext.New(
                DirectoryCreator.ByteBuffers(),
                populator
        );

        GigaMap<PopEntity> gigaMap = GigaMap.New();
        try (LuceneIndex<PopEntity> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext))) {

            PopEntity entity1 = new PopEntity("name", 42, 1234567890L, 3.14f, 2.71);
            PopEntity entity2 = new PopEntity("name2", 43, 1234567891L, 3.15f, 2.72);
            PopEntity entity3 = new PopEntity("name3", 44, 1234567892L, 3.16f, 2.73);
            PopEntity entity4 = new PopEntity("name4", 45, 1234567893L, 3.17f, 2.74);
            gigaMap.addAll(entity1, entity2, entity3, entity4);


            List<PopEntity> entities = new ArrayList<>();
            luceneIndex.query("name:name", (entityId, entity, score) -> {
                entities.add(entity);
            });
            assertAll(() -> {
                assertEquals(1, entities.size());
                assertEquals(entity1, entities.get(0));
            });

            entities.clear();
            Query intQuery = IntPoint.newExactQuery("age", 42);
            luceneIndex.query(intQuery, (id, entity, score) -> {
                entities.add(entity);
            });
            assertAll(() -> {
                assertEquals(1, entities.size());
                assertEquals(entity1, entities.get(0));
            });

            entities.clear();
            Query longQuery = LongPoint.newExactQuery("id", 1234567890L);
            luceneIndex.query(longQuery, (id, entity, score) -> {
                entities.add(entity);
            });
            assertAll(() -> {
                assertEquals(1, entities.size());
                assertEquals(entity1, entities.get(0));
            });

            entities.clear();
            Query floatQuery = FloatPoint.newExactQuery("score", 3.15f);
            luceneIndex.query(floatQuery, (id, entity, score) -> {
                entities.add(entity);
            });
            assertAll(() -> {
                assertEquals(1, entities.size());
                assertEquals(entity2, entities.get(0));
            });

            entities.clear();
            Query doubleQuery = DoublePoint.newExactQuery("price", 2.71);
            luceneIndex.query(doubleQuery, 10, (id, entity, score) -> {
                entities.add(entity);
            });
            assertAll(() -> {
                assertEquals(1, entities.size());
                assertEquals(entity1, entities.get(0));
            });

        }
    }

    private static class PopDocumentPopulator extends DocumentPopulator<PopEntity>
    {

        @Override
        public void populate(Document document, PopEntity entity)
        {
            document.add(createStringField("name", entity.getName()));
            document.add(createIntField("age", entity.getAge()));
            document.add(createLongField("id", entity.getId()));
            document.add(createFloatField("score", entity.getScore()));
            document.add(createDoubleField("price", entity.getPrice()));
        }
    }

    private static class PopEntity
    {
        private final String name;
        private final int age;
        private final long id;
        private final float score;
        private final double price;

        public PopEntity(String name, int age, long id, float score, double price)
        {
            this.name = name;
            this.age = age;
            this.id = id;
            this.score = score;
            this.price = price;
        }

        public String getName()
        {
            return name;
        }

        public int getAge()
        {
            return age;
        }

        public long getId()
        {
            return id;
        }

        public float getScore()
        {
            return score;
        }

        public double getPrice()
        {
            return price;
        }
    }
}
