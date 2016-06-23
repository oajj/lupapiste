LUPAPISTE.SutiAdminModel = function( params ) {
  "use strict";
  var self = this;

  var service = lupapisteApp.services.sutiService;
  service.fetchAdminDetails();
  service.fetchOperations();

  self.organization = params.organization;

  self.enabled = self.organization.sutiEnabled;

  self.www = self.organization.sutiWww;
};
