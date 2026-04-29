package test.eclpse.store;

/*-
 * #%L
 * ittest
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Master runner for the QA zombie-OID verification suite.
 * <p>
 * Runs every {@code Test_NN_*} scenario sequentially, capturing the
 * pass/fail verdict and any thrown exception, and prints a summary table
 * at the end.  Returns a nonzero exit code if any scenario fails.
 */
public class Test_00_MasterRunner
{
    private static final class Outcome
    {
        final String    name;
        final boolean   pass;
        final long      durationMs;
        final Throwable error;
        Outcome(final String n, final boolean p, final long ms, final Throwable e)
        {
            this.name = n; this.pass = p; this.durationMs = ms; this.error = e;
        }
    }

    public static void main(final String[] args) throws Exception
    {
        final List<Outcome> outcomes = new ArrayList<>();

        outcomes.add(run("Test_01_LazyClearThenParentStore", () -> Test_01_LazyClearThenParentStore.run()));
        outcomes.add(run("Test_02_LazyLoadThenUnloadParentStaleRef", () -> Test_02_LazyLoadThenUnloadParentStaleRef.run()));
        outcomes.add(run("Test_03_MultiChannelSafetyNet", () -> Test_03_MultiChannelSafetyNet.run()));
        outcomes.add(run("Test_04_AbandonedStorer", () -> Test_04_AbandonedStorer.run()));
        outcomes.add(run("Test_05_PartialMutationCollectionReplace", () -> Test_05_PartialMutationCollectionReplace.run()));
        outcomes.add(run("Test_06_CyclicSafetyNetSurvivors", () -> Test_06_CyclicSafetyNetSurvivors.run()));
        outcomes.add(run("Test_07_HousekeepingRaceWithStore", () -> Test_07_HousekeepingRaceWithStore.run()));
        outcomes.add(run("Test_08_SafetyNetWithUnloadedLazy", () -> Test_08_SafetyNetWithUnloadedLazy.run()));
        outcomes.add(run("Test_09_SafetyNetDirectOnlyControl", () -> Test_09_SafetyNetDirectOnlyControl.run()));

        printSummary(outcomes);

        boolean anyFailed = false;
        for(final Outcome o : outcomes) if(!o.pass) anyFailed = true;
        System.exit(anyFailed ? 1 : 0);
    }

    private static Outcome run(final String name, final Callable<Boolean> body)
    {
        System.out.println("\n############# RUNNING " + name + " #############\n");
        final long t0 = System.currentTimeMillis();
        try
        {
            final boolean ok = Boolean.TRUE.equals(body.call());
            return new Outcome(name, ok, System.currentTimeMillis() - t0, null);
        }
        catch(final Throwable t)
        {
            t.printStackTrace();
            return new Outcome(name, false, System.currentTimeMillis() - t0, t);
        }
    }

    private static void printSummary(final List<Outcome> outcomes)
    {
        System.out.println();
        System.out.println("===================================================================");
        System.out.println("                       ZOMBIE QA SUMMARY");
        System.out.println("===================================================================");
        System.out.printf("%-50s %6s %8s%n", "Scenario", "Result", "Time(ms)");
        System.out.println("-------------------------------------------------------------------");
        int passes = 0, fails = 0;
        for(final Outcome o : outcomes)
        {
            System.out.printf("%-50s %6s %8d%n", o.name, o.pass ? "PASS" : "FAIL", o.durationMs);
            if(o.error != null)
            {
                System.out.println("    ERROR: " + o.error.getClass().getSimpleName()
                        + ": " + o.error.getMessage());
            }
            if(o.pass) passes++; else fails++;
        }
        System.out.println("-------------------------------------------------------------------");
        System.out.println("Total: " + outcomes.size() + ", PASS: " + passes + ", FAIL: " + fails);
        System.out.println("===================================================================");
    }
}


