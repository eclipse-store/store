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

import one.microstream.ui.StorageProvider;
import one.microstream.ui.WebDriverProvider;
import one.microstream.ui.data.BasicNonPrimitiveArrayTypes;
import one.microstream.ui.data.ComplexType;
import one.microstream.ui.data.EnumTypeSave;
import one.microstream.ui.data.LongList;
import one.microstream.ui.page.LoginPage;
import one.microstream.ui.page.StoragePage;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class StorageSteps {

    private StoragePage storagePage;

    public StorageSteps() {
        storagePage = new StoragePage(WebDriverProvider.determineDriver());

    }

    @Given("^the storage with BasicNonPrimitiveArrayTypes is started$")
    public void the_storage_with_basicnonprimitivearraytypes_is_started() {
        StorageProvider.startStorage(new BasicNonPrimitiveArrayTypes());
    }

    @Given("the storage with LongListTestData is started")
    public void the_storage_with_longlisttestdata_is_started() {
        StorageProvider.startStorage(new LongList());
    }

    @Given("the storage with empty root is started")
    public void the_storage_with_empty_root_is_started() {
        StorageProvider.startEmptyStorage();
    }

    @When("I add new Object into LongListTestData")
    public void i_add_new_object_into_longlisttestdata() {
        BasicNonPrimitiveArrayTypes basicNonPrimitiveArrayTypes = new BasicNonPrimitiveArrayTypes();
        basicNonPrimitiveArrayTypes.fillSampleData();
        LongList longList = (LongList) StorageProvider.getTestData();
        longList.setObject(basicNonPrimitiveArrayTypes);
        StorageProvider.store(longList);
    }

    @When("I reload the Microstream Web App")
    public void i_reload_the_microstream_web_app() {
        storagePage.reloadPage();
        // Unlike the Vaadin client (which restored the view from the server session), the Vanilla
        // SPA returns to the connect screen on reload, so reconnect to the same instance.
        new LoginPage(WebDriverProvider.determineDriver()).loginWithURL(WebDriverProvider.appBaseUrl());
    }

    @Given("the storage with complexTypeTestData is started")
    public void the_storage_with_complextypetestdata_is_started() {
        StorageProvider.startStorage(new ComplexType());
    }

    @Given("the storage with class contains enums is started")
    public void the_storage_with_class_contains_enums_is_started() {
        StorageProvider.startStorage(new EnumTypeSave());
    }

    @Then("the page with storage content for complexTypeTestData will be shown")
    public void the_page_with_storage_content_for_complextypetestdata_will_be_shown() {
        storagePage.waitToBeLoad();
        storagePage.openRoot();
        storagePage.checkComplexTypeToBeDisplayed();
    }

    @Then("^the page with storage content for BasicNonPrimitiveArrayTypes will be shown$")
    public void the_page_with_storage_content_for_basicnonprimitivearraytypes_will_be_shown() {
        storagePage.waitToBeLoad();
        storagePage.openRoot();
        storagePage.checkBasicNonPrimitiveArrayTypesIsDisplayed();
    }

    @Then("the page with storage content for LongListTestData will be shown")
    public void the_page_with_storage_content_for_longlisttestdata_will_be_shown() {
        storagePage.waitToBeLoad();
        storagePage.openRoot();
        storagePage.checkBasicLongListTypesIsDisplayed();
    }

    @When("^I open byteValues node$")
    public void i_open_bytevalues_node() {
        storagePage.openNodeWithName("byteValues");
    }

    @Then("^I see exactly the byteValues from BasicNonPrimitiveArrayTypes$")
    public void i_see_exactly_the_bytevalues_from_basicnonprimitivearraytypes() {
        BasicNonPrimitiveArrayTypes b = new BasicNonPrimitiveArrayTypes();
        b.fillSampleData();
        storagePage.checkValueIsVisibleInTree(String.valueOf(b.getByteValues()[0]));
        storagePage.checkValueIsVisibleInTree(String.valueOf(b.getByteValues()[1]));
    }

    @Then("the page with storage content with class contains enum will be shown")
    public void the_page_with_storage_content_with_class_contains_enum_will_be_shown() throws Exception {
        storagePage.waitToBeLoad();
        storagePage.openRoot();
        storagePage.checkEnumTypeBeDisplayed();
    }

    @Then("the message about empty root will be shown")
    public void the_message_about_empty_root_will_be_shown() {
        storagePage.waitToBeLoad();
        storagePage.checkNotYedDefine();
    }
}
