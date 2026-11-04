Feature: Read exact value from saved object

  Scenario: Login into Api and show content
    Given the storage with BasicNonPrimitiveArrayTypes is started
    When I log into api
    Then the page with storage content for BasicNonPrimitiveArrayTypes will be shown
    When I open byteValues node
    Then I see exactly the byteValues from BasicNonPrimitiveArrayTypes
