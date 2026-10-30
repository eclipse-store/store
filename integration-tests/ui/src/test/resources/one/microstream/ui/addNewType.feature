Feature: Add new Java Type into Storage after Viewer Start

  Scenario: Add New Type after start and reload viewer
    Given the storage with LongListTestData is started
    When I log into api
    Then the page with storage content for LongListTestData will be shown
    When I add new Object into LongListTestData
    And I reload the Microstream Web App
    Then the page with storage content for LongListTestData will be shown


