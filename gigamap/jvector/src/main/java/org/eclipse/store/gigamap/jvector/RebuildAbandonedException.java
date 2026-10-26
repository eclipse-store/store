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


import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Thrown when a vector index's deferred load-path graph rebuild was not done, or was abandoned
 * part-way, because {@link VectorIndex#close()} has been requested.
 * <p>
 * Its own type, rather than a plain {@link IllegalStateException}, so that the one expected failure
 * of a close can be told apart from real ones: the background task manager logs a task that died of
 * this at debug level, while any other {@code IllegalStateException} - a duplicate node, invalid
 * vector data, a broken index state - stays an error even during shutdown. It still extends
 * {@code IllegalStateException}, which is what a caller of a closing index has always been told to
 * expect.
 * <p>
 * Thrown by the closing path of {@code VectorIndex.Default#ensureGraphRebuilt()}, and so reaches
 * every caller that needs the graph rebuilt while a close is in progress: a search on the index (see
 * {@link VectorIndex#search(float[], int)}), a mutation, an optimize or a persist. They fail with it,
 * message "is closing; its graph was not rebuilt", rather than work on an incomplete graph. Also
 * thrown by the recovery of the vector file, which gives up with it once a close is requested.
 */
final class RebuildAbandonedException extends IllegalStateException
{
    private static final long serialVersionUID = 1L;

    /**
     * @param indexName the name of the closing index, for the message
     */
    RebuildAbandonedException(final String indexName)
    {
        super("Vector index '" + indexName + "' is closing; its graph was not rebuilt");
    }

    /**
     * Whether {@code failure} or one of its causes is a {@link RebuildAbandonedException}. The cause
     * chain matters because a failure can arrive wrapped by a caller on the way up.
     *
     * @param failure the failure to classify, may be {@code null}
     * @return whether the failure is an abandoned rebuild
     */
    static boolean isCause(final Throwable failure)
    {
        // The whole chain, however deep, with each throwable visited once: a chain with a cycle
        // (a.initCause(b) after b was created with cause a) ends when it reaches one it has seen.
        final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable current = failure; current != null && seen.add(current); current = current.getCause())
        {
            if(current instanceof RebuildAbandonedException)
            {
                return true;
            }
        }
        return false;
    }

}
