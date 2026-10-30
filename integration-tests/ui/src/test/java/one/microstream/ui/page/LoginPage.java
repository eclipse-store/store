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
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Connect screen of the Vanilla-JS storage viewer.
 * Plain HTML: a URL {@code <input>} plus a Connect {@code <button>} (no Vaadin components).
 */
public class LoginPage extends AbstractPage {
    private final By urlInput      = By.id("base-url");
    private final By connectButton = By.id("btn-connect");

    public LoginPage(WebDriver driver) {
        super(driver);
    }

    public void startNewPage() {
        waitToBeLoad();
    }

    @Override
    public void waitToBeLoad() {
        super.waitElementToBeClickable(urlInput);
    }

    public StoragePage loginWithURL(String loginURL) {
        WebElement input = driver.findElement(urlInput);
        input.clear();
        input.sendKeys(loginURL);

        clickLoginButton();
        return new StoragePage(driver);
    }

    public void clickLoginButton() {
        driver.findElement(connectButton).click();
    }
}
