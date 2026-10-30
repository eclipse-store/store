package one.microstream.ui.page;

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

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public abstract class AbstractPage {
    protected WebDriver driver;
    protected WebDriverWait driverWait;
    protected Duration waitForLoad = Duration.ofSeconds(25);

    public AbstractPage(WebDriver driver) {
        this.driver = driver;
        driverWait = new WebDriverWait(driver, waitForLoad);
    }

    abstract void waitToBeLoad();

    public String getPageSource() {
        return driver.getPageSource();
    }

    protected void waitElementToBeClickable(By by) {
        driverWait.until(ExpectedConditions.presenceOfElementLocated(by));
        driverWait.until(ExpectedConditions.elementToBeClickable(by));
    }

    protected void waitElementToBeVisible(By by) {
        driverWait.until(ExpectedConditions.presenceOfElementLocated(by));
        driverWait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    protected WebElement expandRootElement(WebElement element) {
        return (WebElement) ((JavascriptExecutor) driver).executeScript("return arguments[0].shadowRoot", element);
    }

    protected WebElement findTag(WebElement element, String tag) {
        return (WebElement) ((JavascriptExecutor) driver).executeScript("return arguments[0].querySelector('"+tag+"')", element);
    }

    public void reloadPage() {
        driver.navigate().refresh();
    }

}
