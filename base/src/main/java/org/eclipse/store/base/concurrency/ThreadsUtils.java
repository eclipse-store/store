package org.eclipse.store.base.concurrency;

/*-
 * #%L
 * Eclipse Store Base utilities
 * %%
 * Copyright (C) 2023 Eclipse Foundation
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

public final class ThreadsUtils {

    /**
	 * Causes the current thread to sleep the specified amount of milliseconds by calling {@link Thread#sleep(long)}.
            * Should an {@link InterruptedException} of {@link Thread#sleep(long)} occur, this method restored the
	 * interruption state by invoking {@link Thread#interrupted()} and reporting the {@link InterruptedException}
	 * wrapped in a {@link RuntimeException}.<p>
	 * The underlying rationale to this behavior is explained in an internal comment.<br>
	 * In short: generically interrupting a thread while ignoring the application/library state and logic is just
	 * as naive and dangerous as {@link Thread#stop()} is.
	 *
             * @param  millis
	 *         the length of time to sleep in milliseconds
	 *
             * @see Thread#sleep(long)
	 * @see Thread#stop()
	 */
    public static final void sleep(final long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch(final InterruptedException e)
        {
            /*
             * Explanations about the meaning of InterruptedException like the following are naive:
             * https://stackoverflow.com/questions/3976344/handling-interruptedexception-in-java
             *
             * Interrupting an application's (/library's) internal thread that has a certain purpose, state and
             * dependency to other parts of the application state by a generic technical is pretty much the same
             * dangerous nonsense as Thread#stop:
             * A thread embedded in a complex context and state can't be stopped or interrupted "just like that".
             * What should a crucial thread do on such a request? Say a thread that updates a database's lock file
             * to indicate it is actively used. Should it just terminate and stop updating the lock file that secures
             * the database despite the other threads still accessing the database? Surely not.
             * Or should it prematurely write the update, because "some doesn't want to wait any longer"? Nonsense.
             * No external interference bypassing the specific logic and ignoring the state and complexity of the
             * application makes sense. It is pure and utter nonsense to interrupt such a thread in such a generic
             * and ignorant way.
             *
             * Whoever (in terms of program logic, of course) wants a certain thread to stop must use the proper
             * methods to do so, that properly control the application state, etc.
             * If there are none provided, then the thread is not supposed to be stoppable or interrupt-able.
             * It's that simple.
             *
             * If a managing layer (like the OS) wants to shut down the application, it has to use its proper
             * interfacing means for that, but never pick out single threads and stop or interrupt them one by one.
             * Generic interruption CAN be useful IF the logic explicitly supports it.
             * Otherwise, this is just another JDK naivety that does more harm than good.
             * Thread#stop has been deprecated and so should generic interruption be.
             */

            // restore the interruption flag
            Thread.currentThread().interrupt();

            // abort the current program flow and report back the inconsistent program behavior.
            throw new RuntimeException(e);
        }
    }

    public static final Thread start(final Runnable runnable)
    {
        final Thread t = new Thread(runnable);
        t.start();
        return t;
    }

    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    /**
     * Dummy constructor to prevent instantiation of this static-only utility class.
     *
     * @throws UnsupportedOperationException when called
     */
    private ThreadsUtils()
    {
        // static only
        throw new UnsupportedOperationException();
    }
}
