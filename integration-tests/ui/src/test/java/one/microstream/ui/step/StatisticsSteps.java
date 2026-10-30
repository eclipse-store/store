package one.microstream.ui.step;

/*-
 * #%L
 * EclipseStore Integration Tests - UI
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

import one.microstream.ui.WebDriverProvider;
import one.microstream.ui.page.StoragePage;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class StatisticsSteps {

    private final StoragePage storagePage;

    public StatisticsSteps() {
        storagePage = new StoragePage(WebDriverProvider.determineDriver());
    }

    @When("^I switch to statistics$")
    public void switchToStatistics() {
        storagePage.switchToStatistics();
    }

    @Then("^the page with statistics will be shown$")
    public void checkStatisticsIsShown() {
        storagePage.checkStatisticsIsShown();
    }
}
