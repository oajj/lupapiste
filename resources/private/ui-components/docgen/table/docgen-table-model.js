LUPAPISTE.DocgenTableModel = function(params) {
  "use strict";
  var self = this;

  // inherit from DocgenGroupModel
  ko.utils.extend(self, new LUPAPISTE.DocgenGroupModel(params));

  self.columnHeaders = _.map(params.subSchema.body, function(schema) {
    return self.path.concat(schema.name);
  });
  
};