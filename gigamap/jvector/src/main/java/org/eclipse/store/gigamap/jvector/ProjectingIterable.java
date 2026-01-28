package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
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

import java.util.Iterator;
import java.util.function.Function;

class ProjectingIterable<T, R> implements Iterable<R>
{
    private final Iterable<T>    source    ;
    private final Function<T, R> projection;

    ProjectingIterable(final Iterable<T> source, final Function<T, R> projection)
    {
        this.source     = source    ;
        this.projection = projection;
    }

    @Override
    public Iterator<R> iterator()
    {
        return new ProjectingIterator<>(this.source.iterator(), this.projection);
    }



    static class ProjectingIterator<T, R> implements Iterator<R>
    {
        private final Iterator<T>    source    ;
        private final Function<T, R> projection;

        ProjectingIterator(final Iterator<T> source, final Function<T, R> projection)
        {
            this.source = source;
            this.projection = projection;
        }

        @Override
        public boolean hasNext()
        {
            return this.source.hasNext();
        }

        @Override
        public R next()
        {
            return this.projection.apply(this.source.next());
        }

        @Override
        public void remove()
        {
            this.source.remove();
        }

    }

}
