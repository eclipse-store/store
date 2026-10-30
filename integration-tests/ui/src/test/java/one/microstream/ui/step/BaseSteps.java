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

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import one.microstream.ui.StorageProvider;
import one.microstream.ui.WebDriverProvider;

import io.cucumber.java.After;
import io.cucumber.java.Scenario;

public class BaseSteps {

    @After
    public void after(Scenario scenario) throws Exception {
        if (scenario.isFailed()) {
            WebDriver driver = WebDriverProvider.determineDriver();
            scenario.attach("Current Page URL is " + driver.getCurrentUrl(), "text/plain", "URL:");
            scenario.log("Current Page URL is " + driver.getCurrentUrl());
            byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            scenario.attach(screenshot, "image/png", "Screenshot");
        }
        WebDriverProvider.closeDriver();
        StorageProvider.closeStorage();
    }
}
