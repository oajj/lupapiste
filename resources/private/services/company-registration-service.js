LUPAPISTE.CompanyRegistrationService = function() {
  "use strict";
  var self = this;

  var accountPrices = ko.observable({account5: 59, account15: 79, account30: 99 });

  // User summary or null.
  function user() {
    var u = lupapisteApp.models.currentUser;
    return u.id()
         ? ko.mapping.toJS( _.pick( u, ["firstName", "lastName", "email"]))
         : null;
  }

  function newRegistration() {
    return {
      accountType: ko.observable(),
      name: ko.observable(),
      y: ko.observable(),
      address1: ko.observable(),
      zip: ko.observable(),
      po: ko.observable(),
      country: ko.observable(),
      netbill: ko.observable(),
      pop: ko.observable(),
      reference: ko.observable(),
      firstName: ko.observable(),
      lastName: ko.observable(),
      email: ko.observable(),
      personId: ko.observable(),
      language: ko.observable( loc.currentLanguage )
    };
  }

  function requiredFields() {
    var fields = ["name", "y",
                  "address1", "zip", "po"];
    return user()
         ? fields
         : _.concat( fields, [ "firstName", "lastName",
                               "email", "personId"]);
  }

  // Guard makes sure that the wizard components are disposed.
  self.guard = ko.observable( true );
  self.registration = newRegistration();
  // Current step [0-3] in the registration wizard.
  self.currentStep = ko.observable( 0 );

  ko.computed( function() {
    var u = user();
    if( u ) {
      self.registration.firstName( u.firstName );
      self.registration.lastName( u.lastName );
      self.registration.email( u.email );
    }
  });

  var warnings = {
    y: ko.observable(),
    zip: ko.observable(),
    email: ko.observable(),
    personId: ko.observable()
  };

  function isValidZip( s ) {
    s = _.trim( s );
    return _.size( s ) > 4 && /^[0-9]+$/.test( s );
  }

  function isValidEmail( s ) {
    var isOk = util.isValidEmailAddress( s );
    if( isOk && !user() ) {
      ajax.query( "email-in-use", {email: s })
      .success( _.partial( warnings.email, "email-in-use" ))
      .error( _.noop )
      .call();
    }
    return isOk || user();
  }

  var validators = {
    y: {fun: util.isValidY,
              msg: "error.invalidY"},
    zip: {fun: isValidZip,
          msg: "error.illegal-zip"},
    email: {fun: isValidEmail,
            msg: "error.illegal-email"},
    personId: {fun: util.isValidPersonId,
              msg: "error.illegal-hetu"}
  };


  // Warnings are updated.
  ko.computed( function() {
    _.each( validators, function( validator, k ) {
      var txt = _.trim( self.registration[k]());
      warnings[k]( txt && self.guard() && !validator.fun( txt )
                 ? validator.msg
                 : "");
    });
  });

  self.field = function( fieldName ) {
    return {required: _.includes( requiredFields(), fieldName ),
            value: self.registration[fieldName],
            label: "register.company.form." + fieldName,
            warning: warnings[fieldName],
            testId: "register-company-" + fieldName};
  };

  self.accountTypes = ko.computed( function() {
    return _.map( [5, 15, 30], function( n ) {
      return { id: "account" + n,
               title: loc( sprintf( "register.company.account%s.title", n )),
               price: loc( sprintf( "register.company.account%s.price", n),
                          _.get( accountPrices(), "account" + n )),
               description: loc( "register.company.account.description", n )};
    });
  });

  function reset() {
    self.guard( false );
    self.registration = newRegistration() ;
    self.currentStep( 0 );
    self.guard( true );
  }

  self.cancel = function() {
    if( self.currentStep()) {
      self.currentStep( self.currentStep() - 1 );
    } else {
      reset();
      pageutil.openPage( "register");
    }
  };

  function nextStep() {
    self.currentStep( self.currentStep() + 1 );
  }

  function fieldsOk() {
    return _.every( requiredFields(), function( field ) {
      return _.trim(self.registration[field]());
    } )
        && _.every( _.values( warnings) , _.flow( ko.unwrap, _.isEmpty ));
  }

  var latestSignParams = {};

  self.signResults = ko.observable( {} );
  self.pending = ko.observable();

  function initSign() {
    var reg = _.omitBy( ko.mapping.toJS( self.registration ), _.isBlank );
    var params = {lang: reg.language,
                  company: _.pick( reg,
                                   ["accountType", "name", "y", "address1",
                                    "zip", "po", "country", "netbill",
                                    "pop", "reference"]),
                  signer: _.pick( reg,
                                 ["firstName", "lastName", "email",
                                  "personId", "language"])};
    if( !_.isEqual( latestSignParams, params )) {
      ajax.command( "init-sign", params )
      .pending( self.pending )
      .success( function( res ) {
        latestSignParams = params;
        self.signResults( _.reduce(  _.omit( res, "ok" ),
                                     function( acc, v, k ) {
                                      return _.set( acc, _.camelCase( k ), v );
                                     },
                                     {}));
        nextStep();
      })
      .call();
    } else {
      nextStep();
    }
  }

  var stepConfigs = [{component: "register-company-account-type",
                      continueEnable: self.registration.accountType,
                      continueClick: nextStep},
                     {component: "register-company-info",
                      continueEnable: fieldsOk,
                      continueClick: initSign},
                    {component: "register-company-sign",
                     noButtons: true}];

  self.currentConfig = function() {
    return stepConfigs[self.currentStep()];
  };

  self.devFill = function() {
    self.registration.name( "Foobar Oy");
    self.registration.y( "0000000-0");
    self.registration.address1( "Katuosoite 1");
    self.registration.zip( "12345");
    self.registration.po( "Kaupunki");
    if( !user()) {
      self.registration.firstName( "Etunimi");
      self.registration.lastName( "Sukunimi");
      self.registration.email( "foo@example.com");
      self.registration.personId( "150805-325W" );
    }
  };
};
