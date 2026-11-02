Feature: Login and show message with empty root

  Scenario: Login into Api and show content
    Given the storage with empty root is started
    When I log into api
    Then the message about empty root will be shown