package one.microstream.ui;

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

import java.net.MalformedURLException;
import java.net.URL;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import io.github.bonigarcia.wdm.WebDriverManager;

public class WebDriverProvider {

    private static WebDriver driver;

    public static WebDriver createWebDriver() {

        String host = System.getenv("HUB_HOST");
        String port = System.getenv("HUB_PORT");

        WebDriver driver = null;

        if (null != host) {
            try {
                driver = new RemoteWebDriver(new URL("http://" + host + ":" + port + "/wd/hub"), new ChromeOptions());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        } else {
            WebDriverManager.chromedriver().setup();
            final ChromeOptions options = new ChromeOptions();
            if (!"true".equalsIgnoreCase(System.getenv("UI_HEADED"))) {
                // CI runners have a Chrome but no display; UI_HEADED=true shows the browser locally.
                options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--window-size=1920,1080");
            }
            driver = new ChromeDriver(options);
        }

        return driver;
    }

    public static void closeDriver() {
        driver.quit();
        driver = null; //important. Driver is closed but not null. So by second test will be not recreated.
    }

    public static WebDriver startDriver() {
        // Only create the driver here. Navigation to the app is deferred until after the REST
        // service (which serves the Vanilla-JS viewer, same origin) has been started by the
        // @Given step — otherwise the browser hits a not-yet-listening :4567 and gets
        // ERR_CONNECTION_REFUSED while Cucumber is still instantiating the glue classes.
        driver = createWebDriver();
        return driver;
    }

    public static WebDriver determineDriver() {
        return null != driver ? driver : startDriver();
    }

    private static String appHost() {
        String host = System.getenv("APP_HOST");
        return null != host ? host : "localhost";
    }

    /** Open the bundled viewer served by the Javalin REST service. */
    public static void openApp() {
        determineDriver().get("http://" + appHost() + ":4567/");
    }

    /** Base URL to connect to — same origin as the served viewer. */
    public static String appBaseUrl() {
        return "http://" + appHost() + ":4567/store-data/";
    }
}
