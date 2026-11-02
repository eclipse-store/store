Feature: Login and show content

  Scenario: Login into Api and show content
    Given the storage with BasicNonPrimitiveArrayTypes is started
    When I log into api
    Then the page with storage content for BasicNonPrimitiveArrayTypes will be shown