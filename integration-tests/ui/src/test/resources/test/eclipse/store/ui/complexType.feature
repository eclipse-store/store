Feature: Read values from Complex type class

  Scenario: Login into Api and show content for complex class
    Given the storage with complexTypeTestData is started
    When I log into api
    Then the page with storage content for complexTypeTestData will be shown
