Feature: Show statistics tab

  Scenario: Login into Api and show statistics
    Given the storage with BasicNonPrimitiveArrayTypes is started
    When I log into api
    Then the page with storage content for BasicNonPrimitiveArrayTypes will be shown
    When I switch to statistics
    Then the page with statistics will be shown

