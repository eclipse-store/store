package org.eclipse.store.gigamap;

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

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

/**
 * Global TestExecutionListener for the gigamap module.
 * Registered via META-INF/services so it runs before any tests in the module.
 */
public class GlobalTestListener implements TestExecutionListener {

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        System.out.println("[GlobalTestListener] testPlanExecutionStarted: global setup before all tests");
        // TODO: initialize shared test resources here (e.g. temporary directories, DB, logging)
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        System.out.println("[GlobalTestListener] testPlanExecutionFinished: global cleanup after all tests");
        // TODO: cleanup shared test resources here
    }
}

