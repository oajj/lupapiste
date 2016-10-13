*** Settings ***

Documentation  Attachment related resources

*** Keywords ***

Rollup approved
  [Arguments]  ${name}
  Scroll to  rollup-status-button[data-test-name='${name}'] button.rollup-button  
  Wait until  Element should be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button.positive

Rollup rejected
  [Arguments]  ${name}
  Scroll to  rollup-status-button[data-test-name='${name}'] button.rollup-button  
  Wait until  Element should be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button
  Element should be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button span.lupicon-circle-attention

Rollup neutral
  [Arguments]  ${name}
  Scroll to  rollup-status-button[data-test-name='${name}'] button.rollup-button  
  Wait until  Element should be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button
  Element should not be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button.positive
  Element should not be visible  jquery=rollup-status-button[data-test-name='${name}'] button.rollup-button span.lupicon-circle-attention

Approve row
  [Arguments]  ${row}
  Scroll and click  ${row} button.approve

Reject row
  [Arguments]  ${row}
  Scroll and click  ${row} button.reject

Remove row
  [Arguments]  ${row}
  Scroll and click  ${row} button[data-test-icon=delete-button]
  Confirm yes no dialog
