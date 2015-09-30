LUPAPISTE.ApplicationsSearchFiltersListModel = function(params) {
  "use strict";
  var self = this;

  var dataProvider = params.dataProvider || {};

  self.showSavedFilters = ko.observable(false);

  self.savedFilters = lupapisteApp.services.applicationFiltersService.savedFilters;

  self.newFilterName = ko.observable().extend({
    validation: {
      validator: function (val) {
        return _.isEmpty(_.find(self.savedFilters(), function(f) {
          return val === ko.unwrap(f.title);
        }));
      },
      message: loc("applications.search.filter-name-already-in-use")
    }
  });

  self.saveFilter = function() {
    var title = self.newFilterName();

    var filter = {
      handlers:      _.map(ko.unwrap(lupapisteApp.services.handlerFilterService.selected), "id"), //util.getIn(self.dataProvider, ["handler", "id"]),
      tags:          _.map(ko.unwrap(lupapisteApp.services.tagFilterService.selected), "id"),
      operations:    _.map(ko.unwrap(lupapisteApp.services.operationFilterService.selected), "id"),
      organizations: _.map(ko.unwrap(lupapisteApp.services.organizationFilterService.selected), "id"),
      areas:         _.map(ko.unwrap(lupapisteApp.services.areaFilterService.selected), "id")
    };

    // TODO foreman
    ajax
    .command("save-application-filter", {filterType: "application", title: title, filter: filter, sort: ko.toJS(dataProvider.sort)})
    .error(util.showSavedIndicator)
    .success(function(res) {
      util.showSavedIndicator(res);
      lupapisteApp.services.applicationFiltersService.addFilter(res.filter);
      self.newFilterName("");
      self.showSavedFilters(true);
    })
    .call();
  };

  self.clearFilters = function() {
    lupapisteApp.services.handlerFilterService.selected([]);
    lupapisteApp.services.tagFilterService.selected([]);
    lupapisteApp.services.operationFilterService.selected([]);
    lupapisteApp.services.organizationFilterService.selected([]);
    lupapisteApp.services.areaFilterService.selected([]);
    lupapisteApp.services.applicationFiltersService.selected(undefined);
    dataProvider.searchField("");
    self.newFilterName("");
  };

};
