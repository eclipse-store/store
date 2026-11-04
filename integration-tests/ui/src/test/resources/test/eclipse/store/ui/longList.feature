Feature: Read values from the array of 10 000 items

  Scenario: Login into Api and show content
    Given the storage with LongListTestData is started
    When I log into api
    Then the page with storage content for LongListTestData will be shown
