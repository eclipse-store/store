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

import java.util.List;

import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Data / Statistics view of the Vanilla-JS storage viewer.
 * <p>
 * The tree is a plain HTML table: each row is a {@code <tr class="tree-row" data-depth="N">},
 * the node name lives in a {@code <span class="field-name">}, and the expand/collapse control is a
 * {@code <span class="tree-toggle">}. This mirrors the original Vaadin grid, where the tree level was
 * exposed via the {@code --_level} style; here it is the {@code data-depth} attribute.
 */
public class StoragePage extends AbstractPage {

    private final String rowsAtLevel = "//div[@id='data-tree']//tr[@data-depth='%s']";

    private final By rootRow        = By.xpath("//div[@id='data-tree']//tr[@data-depth='0']");
    private final By rootToggle     = By.xpath("//div[@id='data-tree']//tr[@data-depth='0']//span[@class='tree-toggle']");
    private final By firstMemberRow = By.xpath("(//div[@id='data-tree']//tr[@data-depth='1'])[1]");

    private final By statisticsTab   = By.cssSelector("button.tab[data-tab='statistics']");
    private final By statisticsItems = By.xpath("//div[@id='statistics-view']//tr[@data-depth='0']");

    private final By notYetDefineElement =
        By.xpath("//div[@id='data-tree']//td[normalize-space(.)='NOT YET DEFINED']");

    private final String valueLocator = "//div[@id='data-tree']//td[normalize-space(.)='%s']";

    public StoragePage(WebDriver driver) {
        super(driver);
    }

    @Override
    public void waitToBeLoad() {
        super.waitElementToBeClickable(rootRow);
    }

    public void openRoot() {
        driver.findElement(rootToggle).click();
    }

    public void switchToStatistics() {
        driver.findElement(statisticsTab).click();
    }

    public void checkNotYedDefine() {
        WebElement el = driver.findElement(notYetDefineElement);
        Assert.assertEquals("NOT YET DEFINED", el.getText());
    }

    public void checkBasicNonPrimitiveArrayTypesIsDisplayed() {
        checkMembersElementsInLevel("byteValues", 1);
        checkMembersElementsInLevel("shortValues", 1);
    }

    public void checkBasicLongListTypesIsDisplayed() {
        checkMembersElementsInLevel("array", 1);
        openNodeWithNameInLevel("array", 1);
        waitToLevelItemDisplayed(2);
        checkMembersElementsInLevel("[0..9999]", 2);
        checkMembersElementsInLevel("[10000..19999]", 2);
        checkMembersElementsInLevel("[20000..29999]", 2);
        checkMembersElementsInLevel("[30000..39999]", 2);
        openNodeWithNameInLevel("[0..9999]", 2);

        waitToLevelItemDisplayed(3);

        checkMembersElementsInLevel("[0..99]", 3);
        checkMembersElementsInLevel("[100..199]", 3);
        checkMembersElementsInLevel("[200..299]", 3);
        checkMembersElementsInLevel("[300..399]", 3);
        openNodeWithNameInLevel("[0..99]", 3);
        waitToLevelItemDisplayed(4);

        checkMembersElementsInLevel("[0]", 4);
    }

    public void checkComplexTypeToBeDisplayed() {
        checkMembersElementsInLevel("basicNonPrimitive", 1);
        checkMembersElementsInLevel("integer", 1);
        checkMembersElementsInLevel("longList", 1);
        openNodeWithNameInLevel("longList", 1);
        waitToLevelItemDisplayed(2);

        openNodeWithNameInLevel("array", 2);
        waitToLevelItemDisplayed(3);

        checkMembersElementsInLevel("[0..9999]", 3);
        checkMembersElementsInLevel("[10000..19999]", 3);
        checkMembersElementsInLevel("[20000..29999]", 3);
        checkMembersElementsInLevel("[30000..39999]", 3);
    }

    public void checkEnumTypeBeDisplayed() throws InterruptedException {
        checkMembersElementsInLevel("first", 1);
        checkMembersElementsInLevel("second", 1);
        openNodeWithNameInLevel("first", 1);
        waitToLevelItemDisplayed(2);
        checkMembersElementsInLevel("name", 2);
        checkMembersElementsInLevel("ordinal", 2);
        checkMembersElementsInLevel("value", 2);
        checkValueIsVisibleInTree("\"FIRST\"");
    }

    public By composeLevel(int level) {
        return By.xpath(String.format(rowsAtLevel, level));
    }

    private String nameOf(WebElement row) {
        return row.findElement(By.className("field-name")).getText();
    }

    public WebElement findMemberElementByNameInLevel(String textValue, int level) {
        super.waitElementToBeClickable(firstMemberRow);
        List<WebElement> rows = driver.findElements(composeLevel(level));
        return rows.stream().filter(r -> nameOf(r).equals(textValue)).findFirst().orElse(null);
    }

    public void checkMembersElementsInLevel(String s, int level) {
        super.waitElementToBeClickable(firstMemberRow);
        List<WebElement> rows = driver.findElements(composeLevel(level));
        WebElement el = rows.stream().filter(r -> nameOf(r).equals(s)).findFirst().orElse(null);
        Assert.assertNotNull(s, el);
    }

    public void checkStatisticsIsShown() {
        super.waitElementToBeVisible(statisticsItems);
        List<WebElement> elements = driver.findElements(statisticsItems);
        Assert.assertTrue(elements.size() > 3);
    }

    public void openNodeWithName(String name) {
        openNodeWithNameInLevel(name, 1);
    }

    public void openNodeWithNameInLevel(String name, int level) {
        WebElement row = findMemberElementByNameInLevel(name, level);
        Assert.assertNotNull(name, row);
        row.findElement(By.xpath(".//span[@class='tree-toggle']")).click();
    }

    public void checkValueIsVisibleInTree(String value) {
        By locator = By.xpath(String.format(valueLocator, value));
        waitElementToBeVisible(locator);
        WebElement el = driver.findElement(locator);
        Assert.assertNotNull(value, el);
    }

    public void waitToLevelItemDisplayed(int level) {
        waitElementToBeVisible(By.xpath("(" + String.format(rowsAtLevel, level) + ")[1]"));
    }
}
