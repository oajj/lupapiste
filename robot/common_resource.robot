*** Settings ***

Documentation  Common stuff for the Lupapiste Functional Tests.
...            More about robot http://code.google.com/p/robotframework/.
Library        Selenium2Library   timeout=10  run_on_failure=Log Source

*** Variables ***

${SERVER}                       http://localhost:8000
${WAIT_DELAY}                   10
${BROWSER}                      firefox
${DEFAULT_SPEED}                0
${OP_TREE_SPEED}                0.1
${SLOW_SPEED}                   0.2
${SLOWEST_SPEED}                0.5

${LOGIN URL}                    ${SERVER}/app/fi/welcome#!/login
${LOGOUT URL}                   ${SERVER}/app/fi/logout
${APPLICATIONS PATH}            /app/fi/applicant#!/applications
${AUTHORITY APPLICATIONS PATH}  /app/fi/authority#!/applications
${FIXTURE URL}                  ${SERVER}/dev/fixture
${CREATE URL}                   ${SERVER}/dev/create
${LAST EMAIL URL}                    ${SERVER}/api/last-email
${SELENIUM}                     ${EMPTY}

*** Keywords ***

Browser
  [Arguments]  ${url}
  Open browser  ${url}  ${BROWSER}   remote_url=${SELENIUM}

Open browser to login page
  Browser  ${LOGIN URL}
  Maximize browser window
  Set selenium speed  ${DEFAULT_SPEED}
  Title should be  Lupapiste
  Wait Until  Page should contain  Haluan kirjautua palveluun

Go to login page
  Go to  ${LOGIN URL}
  Wait Until  Title should be  Lupapiste
  Wait Until  Page should contain  Haluan kirjautua palveluun

Go to last email
  Go to  ${LAST EMAIL URL}
  Wait until  Element should be visible  //*[@id='subject']

Applications page should be open
  Location should contain  ${APPLICATIONS PATH}
  Title should be  Lupapiste
  Wait Until  Element should be visible  xpath=//*[@data-test-id='own-applications']

Authority applications page should be open
  Location should contain  ${AUTHORITY APPLICATIONS PATH}
  #Title should be  Lupapiste - Viranomainen
  Wait Until  Element should be visible  xpath=//*[@data-test-id='own-applications']

Authority-admin front page should be open
  Wait until  Element should be visible  admin

Admin front page should be open
  Wait until  Element should be visible  admin

Number of visible applications
  [Arguments]  ${amount}
  Xpath Should Match X Times  //section[@id='applications']//tr[contains(@class,'application')]  ${amount}

Number of visible inforequests
  [Arguments]  ${amount}
  Xpath Should Match X Times  //section[@id='applications']//tr[contains(@class,'inforequest')]  ${amount}

Wait and click
  [Arguments]  ${element}
  Wait until  Element should be visible  ${element}
  # for IE8
  Focus  ${element}
  Wait until  Element should be visible  ${element}
  Click element  ${element}

Wait until
  [Arguments]  ${keyword}  @{varargs}
  Wait Until Keyword Succeeds  ${WAIT_DELAY}  0.1  ${keyword}  @{varargs}

Wait for jQuery
  Wait For Condition  return (typeof jQuery !== "undefined") && jQuery.active===0;  15

Kill dev-box
  Execute Javascript  $(".dev-debug").hide();


#
# Navigation
#

Go to page
  [Arguments]  ${page}
  Wait for jQuery
  Execute Javascript  window.location.hash = "!/${page}";
  Wait until  Element should be visible  ${page}

Open tab
  [Arguments]  ${name}
  Click by test id  application-open-${name}-tab
  Tab should be visible  ${name}

Tab should be visible
  [Arguments]  ${name}
  Wait until  Element should be visible  application-${name}-tab

Side panel should be visible
  [Arguments]  ${name}
  Wait until  Element should be visible  ${name}-panel

Side panel should not be visible
  [Arguments]  ${name}
  Wait until  Element should not be visible  ${name}-panel

Logout
  Wait for jQuery
  ${secs} =  Get Time  epoch
  Go to  ${LOGOUT URL}?s=${secs}
  Wait Until  Page should contain  Haluan kirjautua palveluun

Open side panel
  [Arguments]  ${name}
  ${sidePanelClosed} =  Run Keyword And Return Status  Element should not be visible  ${name}-panel
  Run keyword If  ${sidePanelClosed}  Click by id  open-${name}-side-panel
  Side panel should be visible  ${name}

Close side panel
  [Arguments]  ${name}
  ${sidePanelOpen} =  Run Keyword And Return Status  Element should be visible  ${name}-panel
  Run keyword If  ${sidePanelOpen}  Click by id  open-${name}-side-panel
  Side panel should not be visible  ${name}

Open last email
  Go to  ${SERVER}/api/last-email

#
# Login stuff
#

User should not be logged in
  # Wait for login query to complete
  Wait for jQuery
  Wait Until  User is not logged in

User is not logged in
  Location should be  ${LOGIN URL}
  Page should contain  Haluan kirjautua palveluun
  # test that no data is bind.

Login
  [Arguments]  ${username}  ${password}
  Wait until  Element should be visible  login-username
  Input text  login-username  ${username}
  Input text  login-password  ${password}
  # for IE8
  Wait and click  login-button

Login fails
  [Arguments]  ${username}  ${password}
  Login  ${username}  ${password}
  User should not be logged in

User should be logged in
  [Arguments]  ${name}
  Wait Until  Element text should be  user-name  ${name}

User logs in
  [Arguments]  ${login}  ${password}  ${username}
  Login  ${login}  ${password}
  User should be logged in  ${username}
  Kill dev-box

Applicant logs in
  [Arguments]  ${login}  ${password}  ${username}
  User logs in  ${login}  ${password}  ${username}
  User role should be  applicant
  User nav menu is visible
  Applications page should be open

Authority logs in
  [Arguments]  ${login}  ${password}  ${username}
  User logs in  ${login}  ${password}  ${username}
  User role should be  authority
  User nav menu is visible
  Authority applications page should be open

Authority-admin logs in
  [Arguments]  ${login}  ${password}  ${username}
  User logs in  ${login}  ${password}  ${username}
  User nav menu is visible
  Authority-admin front page should be open

Admin logs in
  [Arguments]  ${login}  ${password}  ${username}
  User logs in  ${login}  ${password}  ${username}
  User role should be  admin
  User nav menu is visible
  Admin front page should be open

User role should be
  [Arguments]  ${expected-role}
  ${user-role}=  Execute JavaScript  return window.currentUser.get().role();
  Should Be Equal  ${expected-role}  ${user-role}

User nav menu is visible
  Element should be visible  //*[@data-test-id='user-nav-menu']

User nav menu is not visible
  Element should not be visible  //*[@data-test-id='user-nav-menu']

As Mikko
  Open browser to login page
  Mikko logs in

As Teppo
  Open browser to login page
  Teppo logs in

As Veikko
  Open browser to login page
  Veikko logs in

As Sonja
  Open browser to login page
  Sonja logs in

As Sipoo
  Open browser to login page
  Sipoo logs in

As Solitaadmin
  Open browser to login page
  Solitaadmin logs in

Mikko logs in
  Applicant logs in  mikko@example.com  mikko123  Mikko Intonen

Teppo logs in
  Applicant logs in  teppo@example.com  teppo69  Teppo Nieminen

Arto logs in
  Authority logs in  arto  arto  Arto Viranomainen

Veikko logs in
  Authority logs in  veikko  veikko  Veikko Viranomainen

Sonja logs in
  Authority logs in  sonja  sonja  Sonja Sibbo

Ronja logs in
  Authority logs in  ronja  sonja  Ronja Sibbo

Sipoo logs in
  Authority-admin logs in  sipoo  sipoo  Simo Suurvisiiri

SolitaAdmin logs in
  Admin logs in  admin  admin  Admin Admin
  Wait until  Element should be visible  admin

#
# Helpers for cases when target element is identified by "data-test-id" attribute:
#

Input text by test id
  [Arguments]  ${id}  ${value}
  Wait until page contains element  xpath=//input[@data-test-id="${id}"]
  Wait until  Element should be visible  xpath=//input[@data-test-id="${id}"]
  Wait until  Element should be enabled  xpath=//input[@data-test-id="${id}"]
  Execute Javascript  $("input[data-test-id='${id}']").val("${value}").change().blur();

Select From List by test id
  [Arguments]  ${id}  ${value}
  Wait until page contains element  xpath=//select[@data-test-id="${id}"]
  Select From List  xpath=//select[@data-test-id="${id}"]  ${value}

Click by id
  [Arguments]  ${id}
  ${selector} =   Set Variable  $("[id='${id}']:visible")
  # 'Click Element' is broken in Selenium 2.35/FF 23 on Windows, using jQuery instead
  Wait For Condition  return ${selector}.length===1;  10
  Execute Javascript  ${selector}.click();

Click by test id
  [Arguments]  ${id}
  ${selector} =   Set Variable  $("[data-test-id='${id}']:visible")
  # 'Click Element' is broken in Selenium 2.35/FF 23 on Windows, using jQuery instead
  Wait For Condition  return ${selector}.length===1;  10
  Execute Javascript  ${selector}.click();

Click enabled by test id
  [Arguments]  ${id}
  ${path} =   Set Variable  xpath=//*[@data-test-id='${id}']
  Wait until  Page Should Contain Element  ${path}
  Wait Until  Element Should Be Enabled  ${path}
  Click by test id  ${id}

#
# Helper for inforequest and application crud operations:
#

Create application the fast way
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${operation}
  Go to  ${CREATE URL}?address=${address}&propertyId=${propertyId}&municipality=${municipality}&operation=${operation}&x=360603.153&y=6734222.95
  Wait until  Element Text Should Be  xpath=//section[@id='application']//span[@data-test-id='application-property-id']  ${propertyId}
  Kill dev-box

Create inforequest the fast way
  [Arguments]  ${address}  ${x}  ${y}  ${municipality}  ${propertyId}  ${operation}  ${message}
  Go to  ${CREATE URL}?infoRequest=true&address=${address}&propertyId=${propertyId}&municipality=${municipality}&operation=${operation}&x=${x}&y=${y}&message=${message}
  Wait until  Element Text Should Be  xpath=//section[@id='inforequest']//span[@data-test-id='inforequest-property-id']  ${propertyId}
  Kill dev-box

Create application
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Prepare new request  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Click by test id  create-application
  Wait Until  Element should be visible  application
  Wait Until  Element Text Should Be  xpath=//span[@data-test-id='application-property-id']  ${propertyId}

Create first application
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Prepare first request  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Click by test id  create-application
  Wait Until  Element should be visible  application
  Wait Until  Element Text Should Be  xpath=//span[@data-test-id='application-property-id']  ${propertyId}

Create inforequest
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${message}  ${permitType}
  Do create inforequest  false  ${address}  ${municipality}  ${propertyId}  ${message}  ${permitType}

Create first inforequest
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${message}  ${permitType}
  Do create inforequest  true  ${address}  ${municipality}  ${propertyId}  ${message}  ${permitType}

Do create inforequest
  [Arguments]  ${isFirstInforequest}  ${address}  ${municipality}  ${propertyId}  ${message}  ${permitType}
  Run Keyword If  '${isFirstInforequest}' == 'true'  Prepare first request  ${address}  ${municipality}  ${propertyId}  ${permitType}
  ...  ELSE  Prepare new request  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Click by test id  create-proceed-to-inforequest
  # Needed for animation to finish.
  Wait until page contains element  xpath=//textarea[@data-test-id="create-inforequest-message"]
  Wait until  Element should be visible  xpath=//textarea[@data-test-id="create-inforequest-message"]
  Input text  xpath=//textarea[@data-test-id="create-inforequest-message"]  ${message}
  Click by test id  create-inforequest
  Confirm  dynamic-ok-confirm-dialog
  Wait Until  Element should be visible  inforequest
  Wait Until  Element Text Should Be  xpath=//span[@data-test-id='inforequest-property-id']  ${propertyId}

Prepare new request
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Go to page  applications
  Click by test id  applications-create-new
  Do prepare new request

Prepare first request
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Go to page  applications
  Click by test id  applications-create-new-inforequest
  Do prepare new request

Do prepare new request
  Input Text  create-search  ${propertyId}
  Click enabled by test id  create-search-button
  Wait until  Element should be visible  xpath=//div[@id='popup-id']//input[@data-test-id='create-property-id']
  Textfield Value Should Be  xpath=//div[@id='popup-id']//input[@data-test-id='create-property-id']  ${propertyId}
  Wait Until  List Selection Should Be  xpath=//div[@id='popup-id']//select[@data-test-id='create-municipality-select']  ${municipality}
  Execute Javascript  $("div[id='popup-id'] input[data-test-id='create-address']").val("${address}").change();

  Set animations off
  Click enabled by test id  create-continue
  Select operation path by permit type  ${permitType}
  Wait until  Element should be visible  xpath=//section[@id="create-part-2"]//div[@class="tree-content"]//*[@data-test-id="create-application"]
  Set animations on


Add empty attachment template
  [Arguments]  ${templateName}  ${topCategory}  ${subCategory}
  Wait Until Element Is Visible  xpath=//div[@id="application-attachments-tab"]//button[@data-test-id="new-attachment-template-button"]
  Click Element  xpath=//div[@id="application-attachments-tab"]//button[@data-test-id="new-attachment-template-button"]
  Wait Until Element Is Visible  xpath=//div[@id="dialog-add-attachment-templates"]//input[@data-test-id="selectm-filter-input"]
  Input Text  xpath=//div[@id="dialog-add-attachment-templates"]//input[@data-test-id="selectm-filter-input"]  ${subCategory}
  List Should Have No Selections  xpath=//div[@id="dialog-add-attachment-templates"]//select[@data-test-id="selectm-source-list"]
  Click Element  xpath=//div[@id="dialog-add-attachment-templates"]//select[@data-test-id="selectm-source-list"]//option[contains(text(), '${templateName}')]
  Click Element  xpath=//div[@id="dialog-add-attachment-templates"]//button[@data-test-id="selectm-add"]
  Click Element  xpath=//div[@id="dialog-add-attachment-templates"]//button[@data-test-id="selectm-ok"]
  Wait Until  Element Should Not Be Visible  xpath=//div[@id="dialog-add-attachment-templates"]//input[@data-test-id="selectm-filter-input"]
  Wait Until Element Is Visible  xpath=//div[@id="application-attachments-tab"]//a[@data-test-type="${topCategory}.${subCategory}"]

Add attachment
  [Arguments]  ${path}  ${description}  ${operation}

  # Go home Selenium, you're drunk! Why the fuck are you clicking the 'process-previous' button?
  # Must I do everything manually??
  #Wait and click   xpath=//button[@data-test-id="add-attachment"]
  Execute Javascript  $('button[data-test-id="add-attachment"]').click();

  Select Frame      uploadFrame
  Wait until        Element should be visible  test-save-new-attachment
  Wait until        Page should contain element  xpath=//form[@id='attachmentUploadForm']//option[@value='muut.muu']
  Select From List  attachmentType  muut.muu
  Wait until        Page should contain element  xpath=//form[@id='attachmentUploadForm']//option[text()='${operation}']
  Select From List  attachmentOperation  ${operation}
  Input text        text  ${description}
  Wait until        Page should contain element  xpath=//form[@id='attachmentUploadForm']/input[@type='file']
  Focus             xpath=//form[@id='attachmentUploadForm']/input[@type='file']
  Choose File       xpath=//form[@id='attachmentUploadForm']/input[@type='file']  ${path}
  # Had to use 'Select Frame' another time to be able to use e.g. 'Element Should Be Enabled'
  # Select Frame      uploadFrame
  # Wait Until        Element Should Be Enabled  test-save-new-attachment
  Click element     test-save-new-attachment
  Unselect Frame
  Wait Until Page Contains  Muu liite

Open attachment details
  [Arguments]  ${type}
  Open tab  attachments
  Wait Until  Page Should Contain Element  xpath=//a[@data-test-type="${type}"]
  # Make sure the element is visible on browser view before clicking. Take header heigth into account.
  #Execute Javascript  window.scrollTo(0, $("[data-test-type='muut.muu']").position().top - 130);
  Focus  xpath=//a[@data-test-type="${type}"]
  Click element  xpath=//a[@data-test-type="${type}"]
  Wait Until  Element Should Be Visible  xpath=//a[@data-test-id="back-to-application-from-attachment"]

Assert file latest version
  [Arguments]  ${name}  ${versionNumber}
  Wait Until  Element Should Be Visible  test-attachment-file-name
  Wait Until Page Contains  ${TXT_TESTFILE_NAME}
  Element Text Should Be  test-attachment-file-name  ${name}
  Element Text Should Be  test-attachment-version  ${versionNumber}

Add first attachment version
  [Arguments]  ${path}
  Wait Until     Element should be visible  xpath=//button[@id="add-new-attachment-version"]
  Click Element  xpath=//button[@id="add-new-attachment-version"]
  Wait Until     Element should be visible  xpath=//*[@id="uploadFrame"]
  Select Frame   uploadFrame
  Wait until     Element should be visible  test-save-new-attachment
  Wait until     Page should contain element  xpath=//form[@id='attachmentUploadForm']/input[@type='file']
  Focus          xpath=//form[@id='attachmentUploadForm']/input[@type='file']
  Choose File    xpath=//form[@id='attachmentUploadForm']/input[@type='file']  ${path}
  # Had to use 'Select Frame' another time to be able to use e.g. 'Element Should Be Enabled'
  # Select Frame   uploadFrame
  # Wait Until     Element Should Be Enabled  test-save-new-attachment
  Click element  test-save-new-attachment
  Unselect Frame
  Wait until     Page should contain element  xpath=//div[@class='attachment-label']

Select operation path by permit type
  [Arguments]  ${permitType}
  Run Keyword If  '${permitType}' == 'R'  Select operations path R
  ...  ELSE IF  '${permitType}' == 'YA-kaivulupa'  Select operations path YA kayttolupa kaivu
  ...  ELSE IF  '${permitType}' == 'YA-kayttolupa'  Select operations path YA kayttolupa
  ...  ELSE IF  '${permitType}' == 'YA-kayttolupa-mainostus-viitoitus'  Select operations path YA kayttolupa mainostus-viitoitus
  ...  ELSE IF  '${permitType}' == 'YA-sijoituslupa'  Select operations path YA sijoituslupa
  ...  ELSE  Select operations path R

Select operations path R
  Click tree item by text  "Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"
  Click tree item by text  "Uuden rakennuksen rakentaminen"
  Click tree item by text  "Asuinkerrostalon tai rivitalon rakentaminen"

Select operations path YA kayttolupa
  Click tree item by text  "Yleiset alueet (Sijoittamissopimus, katulupa, alueiden käyttö)"
  Click tree item by text  "Yleisten alueiden tai muiden kunnan omistamien alueiden käyttö (tapahtumat, mainokset, yms.)"
  Click tree item by text  "Terassin sijoittaminen"

Select operations path YA kayttolupa kaivu
  Click tree item by text  "Yleiset alueet (Sijoittamissopimus, katulupa, alueiden käyttö)"
  Click tree item by text  "Työskentely yleisellä alueella (Katulupa)"
  Click tree item by text  "Kaivaminen yleisellä alueella"
  Click tree item by text  "Vesi- ja viemäritöiden tekeminen"

Select operations path YA kayttolupa mainostus-viitoitus
  Click tree item by text  "Yleiset alueet (Sijoittamissopimus, katulupa, alueiden käyttö)"
  Click tree item by text  "Yleisten alueiden tai muiden kunnan omistamien alueiden käyttö (tapahtumat, mainokset, yms.)"
  Click tree item by text  "Mainoslaitteiden ja opasteviittojen sijoittaminen"

Select operations path YA sijoituslupa
  Click tree item by text  "Yleiset alueet (Sijoittamissopimus, katulupa, alueiden käyttö)"
  Click tree item by text  "Rakenteiden sijoittaminen yleiselle alueelle (Sijoittamissopimus)"
  Click tree item by text  "Pysyvien maanalaisten rakenteiden sijoittaminen"
  Click tree item by text  "Vesi- ja viemärijohtojen sijoittaminen"

Click tree item by text
  [Arguments]  ${itemName}
  Wait and click  //section[@id="create-part-2"]//div[@class="tree-content"]//*[text()=${itemName}]


# Closes the application that is currently open by clicking cancel button
Close current application
  Wait Until  Element Should Be Enabled  xpath=//button[@data-test-id="application-cancel-btn"]
  Click by test id  application-cancel-btn
  Confirm  dynamic-yes-no-confirm-dialog

Confirm
  [Arguments]  ${modalId}
  Wait until  Element should be visible  xpath=//div[@id="${modalId}"]//button[@data-test-id="confirm-yes"]
  Focus  xpath=//div[@id="${modalId}"]//button[@data-test-id="confirm-yes"]
  Click Element  xpath=//div[@id="${modalId}"]//button[@data-test-id="confirm-yes"]
  Wait Until  Element Should Not Be Visible  ${modalId}

It is possible to add operation
  Wait until  Element should be visible  xpath=//button[@data-test-id="add-operation"]

Submit application
  Open tab  requiredFieldSummary
  Click enabled by test id  application-submit-btn
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Application state should be  submitted

#
# Jump to application or inforequest:
#

Open the request
  [Arguments]  ${address}
  Go to page  applications
  Wait until  Click element  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']/td
  Wait for jQuery

Open the request at index
  [Arguments]  ${address}  ${index}
  Go to page  applications
  Wait until  Click element  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}'][${index}]/td
  Wait for jQuery

Open application
  [Arguments]  ${address}  ${propertyId}
  Open the request  ${address}
  Wait until  Element Should Be Visible  application
  Wait until  Element Text Should Be  xpath=//section[@id='application']//span[@data-test-id='application-property-id']  ${propertyId}

Open application at index
  [Arguments]  ${address}  ${propertyId}  ${index}
  Open the request at index  ${address}  ${index}
  Wait until  Element Should Be Visible  application
  Wait until  Element Text Should Be  xpath=//section[@id='application']//span[@data-test-id='application-property-id']  ${propertyId}

Open inforequest
  [Arguments]  ${address}  ${propertyId}
  Open the request  ${address}
  Wait until  Element Should Be Visible  inforequest
  Wait until  Element Text Should Be  xpath=//section[@id='inforequest']//span[@data-test-id='inforequest-property-id']  ${propertyId}

Request should be visible
  [Arguments]  ${address}
  Wait Until  Element should be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']

Request should not be visible
  [Arguments]  ${address}
  Wait Until  Element should not be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']

#
# Comments:
#

Add comment
  [Arguments]  ${message}
  Open side panel  conversation
  Input text  xpath=//div[@id='conversation-panel']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Click by test id  application-new-comment-btn
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[@data-test-id='comments-table']//span[text()='${message}']
  Close side panel  conversation

Open to authorities
  [Arguments]  ${message}
  Open side panel  conversation
  Input text  xpath=//div[@id='conversation-panel']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Click by test id  application-open-application-btn
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[@data-test-id='comments-table']//span[text()='${message}']
  Close side panel  conversation

Input comment
  [Arguments]  ${message}
  Open side panel  conversation
  Input text  xpath=//div[@id='conversation-panel']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Click element  xpath=//div[@id='conversation-panel']//button[@data-test-id='application-new-comment-btn']
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[contains(@class,'comment-text')]//span[text()='${message}']
  Close side panel  conversation

Input inforequest comment
  [Arguments]  ${message}
  Input text  xpath=//section[@id='inforequest']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Click element  xpath=//section[@id='inforequest']//button[@data-test-id='application-new-comment-btn']
  Wait until  Element should be visible  xpath=//section[@id='inforequest']//div[contains(@class,'comment-text')]//span[text()='${message}']

Input comment and open to authorities
  [Arguments]  ${message}
  Open side panel  conversation
  Input text  xpath=//div[@id='conversation-panel']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Click element  xpath=//div[@id='conversation-panel']//button[@data-test-id='application-open-application-btn']
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[contains(@class,'comment-text')]//span[text()='${message}']
  Close side panel  conversation

Input comment and mark answered
  [Arguments]  ${message}
  Input text  xpath=//section[@id='inforequest']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Click element  xpath=//section[@id='inforequest']//button[@data-test-id='comment-request-mark-answered']
  Wait until  element should be visible  xpath=//div[@id='dynamic-ok-confirm-dialog']//button[@data-test-id='confirm-yes']
  Click element  xpath=//div[@id='dynamic-ok-confirm-dialog']//button[@data-test-id='confirm-yes']
  Wait until  element should not be visible  xpath=//div[@id='dynamic-ok-confirm-dialog']
  Wait until  Element should be visible  xpath=//section[@id='inforequest']//div[contains(@class,'comment-text')]//span[text()='${message}']

Mark answered
  Click element  xpath=//section[@id='inforequest']//button[@data-test-id='comment-request-mark-answered']
  Wait until  element should be visible  xpath=//div[@id='dynamic-ok-confirm-dialog']//button[@data-test-id='confirm-yes']
  Click element  xpath=//div[@id='dynamic-ok-confirm-dialog']//button[@data-test-id='confirm-yes']
  Wait until  element should not be visible  xpath=//div[@id='dynamic-ok-confirm-dialog']

Comment count is
  [Arguments]  ${section}  ${amount}
  Open side panel  conversation
  Wait until  Xpath Should Match X Times  //div[@id='conversation-panel']//div[contains(@class,'comment-text')]  ${amount}
  Close side panel  conversation

#
# Tasks
#

Task count is
  [Arguments]  ${type}  ${amount}
  Wait until  Xpath Should Match X Times  //table[@data-bind="foreach: taskGroups"]/tbody/tr[@data-test-type="${type}"]  ${amount}

#
# Quick, jettison the db...
#

Apply minimal fixture now
  Go to  ${FIXTURE URL}/minimal
  Page should contain  true
  Go to login page

#
# Application state check:
#

Application state should be
  [Arguments]  ${state}
  ${s} =  Get Element Attribute  xpath=//div[@data-test-id='application-state']@data-test-state
  Should be equal  ${s}  ${state}

Permit type should be
  [Arguments]  ${type}
  Element Text Should Be  xpath=//span[@data-bind='ltext: permitType']  ${type}

#
# Proxy control:
#

Set integration proxy on
  Execute Javascript  ajax.post("/api/proxy-ctrl/on").call();
  Wait for jQuery
  Execute Javascript  ajax.query("set-feature",{feature:"maps-disabled",value:false}).call();
  Wait for jQuery
  Execute Javascript  ajax.query("set-feature", {feature: "disable-ktj-on-create", value:false}).call();
  Wait for jQuery

Set integration proxy off
  Execute Javascript  ajax.post("/api/proxy-ctrl/off").call();
  Wait for jQuery
  Execute Javascript  ajax.query("set-feature", {feature: "maps-disabled", value:true}).call();
  Wait for jQuery
  Execute Javascript  ajax.query("set-feature", {feature: "disable-ktj-on-create", value:true}).call();
  Wait for jQuery

#
# Animations control:
#

Set animations on
  Execute Javascript  tree.animation(true);

Set animations off
  Execute Javascript  tree.animation(false);

#
# Neighbor
#

Add neighbor
  [Arguments]  ${propertyId}  ${name}  ${email}
  Click enabled by test id  manager-neighbors-add
  Wait Until   Element Should Be Visible  dialog-edit-neighbor
  Input text by test id  neighbors.edit.propertyId  ${propertyId}
  Input text by test id  neighbors.edit.name  ${name}
  Input text by test id  neighbors.edit.email  ${email}
  Click by test id  neighbors.edit.ok
  Wait Until  Element Should Not Be Visible  dialog-edit-neighbor
  Wait Until  Page Should Contain  ${email}

#
# Verdict
#

Go to give new verdict
  Open tab  verdict
  Click enabled by test id  give-verdict
  Wait Until  Element Should Be Visible  backend-id
  Wait Until  Element Should Be Enabled  backend-id

Submit empty verdict
  Go to give new verdict
  Go back
  Click enabled by test id  publish-verdict
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Application state should be  verdictGiven

Fetch verdict
  Click enabled by test id  fetch-verdict
  Wait Until  Element Should Be Visible  dynamic-ok-confirm-dialog
  Element Text Should Be  xpath=//div[@id='dynamic-ok-confirm-dialog']//div[@class='dialog-user-content']/p  Taustajärjestelmästä haettiin 2 kuntalupatunnukseen liittyvät tiedot. Tiedoista muodostettiin 9 uutta vaatimusta Rakentaminen-välilehdelle.
  Confirm  dynamic-ok-confirm-dialog

# User management

Fill in new password
  [Arguments]  ${section}  ${password}
  Wait Until  Page Should Contain  Salasanan vaihtaminen
  Input text  xpath=//section[@id='${section}']//input[@placeholder='Uusi salasana']  ${password}
  Element Should Be Disabled  xpath=//section[@id='${section}']//button
  Input text  xpath=//section[@id='${section}']//input[@placeholder='Salasana uudelleen']  ${password}
  Wait Until  Element Should Be Enabled  xpath=//section[@id='${section}']//button
  Click Element  xpath=//section[@id='${section}']//button
  Go to login page


#
# Mock Ajax calls: jquery.mockjax
#

Mock query
  [Arguments]  ${name}  ${jsonResponse}
  Execute Javascript  $.mockjax({url:'/api/query/${name}', dataType:'json', responseText: ${jsonResponse}});

Mock query error
  [Arguments]  ${name}
  Execute Javascript  $.mockjax({url:'/api/query/${name}', dataType:'json', responseText: {"ok":false, "text":"error.unknown"}});

Mock proxy
  [Arguments]  ${name}  ${jsonResponse}
  Execute Javascript  $.mockjax({url:'/proxy/${name}', dataType:'json', responseText: ${jsonResponse}});

Mock proxy error
  [Arguments]  ${name}
  Execute Javascript  $.mockjax({url:'/proxy/${name}', dataType:'json', status:500});

Clear mocks
  Execute Javascript  $.mockjaxClear();
