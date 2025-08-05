package org.eclipse.store.gigamap.query;

/*-
 * #%L
 * EclipseStore GigaMap
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

import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerFloat;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

public class NumberSearchTest
{

    @FunctionalInterface
    interface TimedAction
    {
        void execute();
    }

    private static void measureTime(final TimedAction action, final String actionDescription)
    {
        final Instant start = Instant.now();
        action.execute();
        final Instant finish = Instant.now();
        final Duration duration = Duration.between(start, finish);
        final long minutes = duration.toMinutesPart();
        final long seconds = duration.toSecondsPart();
        final long millis = duration.toMillisPart();
        final long nanos = duration.toNanosPart();
        System.out.printf("Time taken for: | %-28s | %dm | %ds | %-2dms | %-10dns |%n", actionDescription, minutes, seconds, millis, nanos);
    }

    @Test
    void integerIndexingTest()
    {
        final GigaMap<IntegerObject> gigaMap = this.prepareData(11000);
        final BitmapIndices<IntegerObject> indices = gigaMap.index().bitmap();
        final ValueIndexer valueIndexer = new ValueIndexer();
        indices.add(valueIndexer);

        final FloatIndexer floatIndexer = new FloatIndexer();
        indices.add(floatIndexer);

        measureTime(() ->
                gigaMap.query(valueIndexer.is(integer -> (20 < integer) && (integer < 5000)))
                        .execute((Consumer<? super IntegerObject>) integerObject -> {}), "Between query");

        measureTime(() ->
                gigaMap.query(valueIndexer.is(20))
                        .execute((Consumer<? super IntegerObject>) integerObject -> {}), "Exact value query");
        measureTime(() ->
            gigaMap.query(valueIndexer.is(20).or(valueIndexer.is(30)).or(valueIndexer.is(40)).or(valueIndexer.is(50)))
                    .execute((Consumer<? super IntegerObject>) SintegerObject -> {}), "Exact multiple value query");
        measureTime(() ->
            gigaMap.query(floatIndexer.is(20f)).execute((Consumer<? super IntegerObject>) integerObject -> {}), "Exact float value query");

    }


    private GigaMap<IntegerObject> prepareData(final int count)
    {
        final GigaMap<IntegerObject> gigaMap = GigaMap.New();
        for (int i = 1; i <= count; i++) {
            gigaMap.add(new IntegerObject(i, i, i, i));
        }
        return gigaMap;
    }


    static class IntegerObject
    {
        Integer key;
        int value;
        Integer nonPrimitiveValue;
        float floatValue;

        public IntegerObject(final Integer key, final int value, final Integer nonPrimitiveValue, final float floatValue)
        {
            this.key = key;
            this.value = value;
            this.nonPrimitiveValue = nonPrimitiveValue;
            this.floatValue = floatValue;
        }


        @Override
        public String toString()
        {
            return "IntegerObject{" +
                    "key=" + this.key +
                    ", value=" + this.value +
                    ", nonPrimitiveValue=" + this.nonPrimitiveValue +
                    ", floatValue=" + this.floatValue +
                    '}';
        }
    }

    static class ValueIndexer extends IndexerInteger.Abstract<IntegerObject>
    {
    	@Override
    	protected Integer getInteger(IntegerObject entity)
    	{
            return entity.value;
        }
    }

    static class FloatIndexer extends IndexerFloat.Abstract<IntegerObject>
    {
    	@Override
    	protected Float getFloat(IntegerObject entity)
    	{
            return entity.floatValue;
        }
    }
}
