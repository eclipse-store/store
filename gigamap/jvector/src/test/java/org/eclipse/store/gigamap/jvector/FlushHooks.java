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


import org.eclipse.store.gigamap.jvector.VectorIndex.Default.FlushStage;

/**
 * Test helper for {@link VectorIndex.Default#flushStageTestHook}.
 */
final class FlushHooks
{
    private FlushHooks()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Runs {@code action} once, on the flushing thread with no lock held, when the next flush of
     * {@code index} reaches {@code stage}, and removes the hook before running it.
     *
     * @param index  the index
     * @param stage  the stage to run at
     * @param action what to run there
     */
    static void onceAt(
        final VectorIndex.Default<?> index ,
        final FlushStage             stage ,
        final Runnable               action
    )
    {
        index.flushStageTestHook = reached ->
        {
            if(reached == stage)
            {
                index.flushStageTestHook = null;
                action.run();
            }
        };
    }

}
