Cypress.Commands.add('createInstance', function(group, name, version = '2.0.0') {
  cy.visit('/');
  cy.get('input[hint=Filter]').type(group);
  cy.get('[data-cy=group-' + group + ']').first().click();

  // wait until the progress spinner disappears
  cy.get('mat-progress-spinner').should('not.exist')

  // attention: the button contains 'add' not '+' (mat-icon('add') = '+')
  cy.contains('button', 'add').click();

  cy.get('[placeholder=Name]').type(name)
  cy.get('[placeholder=Description]').type('Test Instance for automated test')

  // angular drop down is something very different from a native HTML select/option
  cy.get('[placeholder=Purpose]').click()
  cy.get('mat-option').contains('TEST').click()

  cy.get('[placeholder=Product]').click()
  cy.get('mat-option').contains('demo/product').click();

  cy.get('[placeholder=Version]').click();
  cy.get('mat-option').contains(version).click();

  // finally the target, which is the configured backend with the configured token.
  cy.get('[placeholder="Master URL"]').type(Cypress.env('backendBaseUrl'))

  cy.get('body').then($body => {
    if($body.find('mat-option:contains(localhost)').length) {
      cy.contains('mat-option', 'localhost').click();
    } else {
      cy.fixture('token.json').then(fixture => {
        // don't use .type(fixture.token) as this mimiks a typing user (delay)
        cy.get('[placeholder="Security Token"]').invoke('val', fixture.token).trigger('input')
      })
    }
  })

  return cy.get('mat-toolbar-row').contains('UUID').get('b').then(el => {
    cy.get('button').contains('SAVE').click();
    return cy.wrap(el.text())
  });

})

Cypress.Commands.add('deleteInstance', function(group, instanceUuid) {
  // make sure we're on the correct page :) this allows delete to work if previous tests failed.
  cy.visit('/#/instance/browser/' + group)

  // open the menu on the card
  cy.contains('mat-card', instanceUuid).clickContextMenuItem('Delete')

  // place a trigger on the endpoint, so we can later wait for it
  cy.server()
  cy.route('GET', '/api/group/Test/instance').as('reload')

  // in the resulting dialog, click OK
  cy.get('mat-dialog-container').contains('button', 'OK').click();

  // wait for the dialog to disappear and the page to reload
  cy.wait('@reload')
  cy.get('mat-progress-spinner').should('not.exist')

  // now NO trace of the UUID should be left.
  cy.get('body').contains(instanceUuid).should('not.exist');
})

Cypress.Commands.add('installAndActivate', {prevSubject: true}, (subject) => {
  cy.get('mat-loading-spinner').should('not.exist');

  // should be in the instance version list now, install
  cy.wrap(subject).clickContextMenuItem('Install')

  // wait for progress and the icon to appear
  cy.wrap(subject).find('mat-progress-spinner').should('not.exist')
  cy.wrap(subject).contains('mat-icon', 'check_circle_outline').should('exist')

  // activate the installed instance version
  cy.wrap(subject).clickContextMenuItem('Activate')

  // wait for progress and the icon to appear
  cy.wrap(subject).find('mat-progress-spinner').should('not.exist')
  cy.wrap(subject).contains('mat-icon', 'check_circle').should('exist')

  // no error should have popped up.
  cy.get('snack-bar-container').should('not.exist')

  return cy.wrap(subject);
})

Cypress.Commands.add('getLatestInstanceVersion', function() {
  cy.get('body').then($body => {
    if($body.find('mat-toolbar:contains("close")').length > 0) {
      cy.contains('button', 'close').click();
    }
  })

  cy.contains('mat-toolbar', 'Instance Versions').should('exist').and('be.visible')
  return cy.get('app-instance-version-card').first()
})

Cypress.Commands.add('getActiveInstanceVersion', function() {
  cy.get('body').then($body => {
    if($body.find('mat-toolbar:contains("close")').length > 0) {
      cy.contains('button', 'close').click();
    }
  })

  return cy.get('mat-card[data-cy=active]').should('have.length', 1).closest('app-instance-version-card')
})

Cypress.Commands.add('addAndSetOptionalParameter', function(panel, name, value) {
  cy.contains('mat-expansion-panel', panel).as('panel');

  cy.get('@panel').click();
  cy.get('@panel').contains('button', 'Manage Optional').click();

  cy.get('[placeholder=Filter').type(name);

  cy.get('mat-dialog-container').contains('td', name).closest('tr').find('mat-checkbox').click();
  cy.get('mat-dialog-container').contains('button', 'Save').click();

  cy.get('@panel').find('[placeholder="' + name + '"]').should('exist')
  cy.get('@panel').find('[placeholder="' + name + '"]').clear().type(value, { parseSpecialCharSequences: false })
})
