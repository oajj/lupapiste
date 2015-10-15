*** Settings ***

Documentation   Admin edits authority admin users
Suite teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Bulletins page is empty at first
  Go to bulletins page
  ${rows}=  Get Matching XPath Count  //table[@id="application-bulletins-list"]/tr
  Should be equal  ${rows}  0

Sonja publishes an application as a bulletin
  Go to login page
  Sonja logs in
  Create application with state  create-app  753-416-25-22  kerrostalo-rivitalo  sent
  Wait until  Element should be visible  //button[@data-test-id='publish-bulletin']
  Click by test id  publish-bulletin
  Logout

Unlogged user sees Sonja's bulletin
  Go to bulletins page
  Wait until  Element should be visible  //table[@id="application-bulletins-list"]//td[contains(text(), "Latokuja 3")]
  ${rows}=  Get Matching XPath Count  //table[@id="application-bulletins-list"]/tbody/tr
  Should be equal  ${rows}  1
