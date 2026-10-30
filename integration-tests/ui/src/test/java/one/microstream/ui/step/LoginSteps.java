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
import one.microstream.ui.data.BasicNonPrimitiveArrayTypes;
import one.microstream.ui.page.LoginPage;
import one.microstream.ui.page.StoragePage;

import io.cucumber.java.en.When;

public class LoginSteps {

    private LoginPage loginPage;
    private StoragePage storagePage;
    private BasicNonPrimitiveArrayTypes basicNonPrimitiveArrayTypes;

    public LoginSteps() {

        loginPage = new LoginPage(WebDriverProvider.determineDriver());

    }

    @When("I log into api")
    public void i_log_into_api() {
        // Navigate now — the REST service (which serves the viewer) has been started by the @Given step.
        WebDriverProvider.openApp();
        loginPage.startNewPage();
        storagePage = loginPage.loginWithURL(WebDriverProvider.appBaseUrl());
    }
}

