Feature: Read values from enum type

  Scenario: Login into Api and show content of Enum
    Given the storage with class contains enums is started
    When I log into api
    Then the page with storage content with class contains enum will be shown
