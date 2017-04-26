;(function() {
  "use strict";

  function CopyApplicationModel() {
    var self = this;

    self.locationSelected = ko.observable(false);
    hub.subscribe("copy-location-selected", function() {
      hub.send("track-click", {category:"Create", label:"map", event:"mapContinue"});
      self.locationSelected(true);
    });

    self.locationModel = new LUPAPISTE.CopyApplicationLocationModel();

    self.search = ko.observable("");
    self.searching = ko.observable(false);

    self.processing = ko.observable(false);
    self.pending = ko.observable(false);
    self.message = ko.observable("");

    self.copyAuths = ko.observableArray([
      {firstName: "Ville",
       lastName: "Outamaa",
       role: "writer",
       roleSource: "auth"},
      {firstName: "Åke W.",
       lastName: "Blomqvist",
       role: "hakija",
       roleSource: "document"}
    ]);

    self.authDescription = function(auth) {
      return auth.firstName + " " + auth.lastName + ", " +
        _.upperFirst(auth.roleSource === "document" ?
                     loc("applicationRole." + auth.role)
                     : loc(auth.role));
    };

    self.findOperations = function(code) {
      municipalities.operationsForMunicipality(code, function(operations) {
        self.operations(operations);
      });
      return self;
    };

    self.resetLocation = function() {
      self.locationModel.reset();
      return self;
    };

    self.clear = function() {
      self.locationModel.clearMap();

      return self
        .resetLocation()
        .search("")
        .message("");
    };

    self.copyOK = ko.pureComputed(function() {
      return self.locationModel.propertyIdOk() && self.locationModel.addressOk() && !self.processing();
    });

    //
    // Callbacks:
    //

    // Search activation:

    self.searchNow = function() {
      hub.send("track-click", {category:"Copy", label:"map", event:"searchLocation"});
      self.locationModel.clearMap().reset();
      self.locationModel.beginUpdateRequest()
        .searchPoint(self.search(), self.searching);
      return false;
    };

    var zoomLevelEnum = {
      "540": 9,
      "550": 7,
      "560": 9
    };

    // Return function that calls every function provided as arguments to 'comp'.
    function comp() {
      var fs = arguments;
      return function() {
        var args = arguments;
        _.each(fs, function(f) {
          f.apply(self, args);
        });
      };
    }

    function zoom(item, level) {
      var zoomLevel = level || zoomLevelEnum[item.type] || 11;
      self.locationModel.center(zoomLevel, item.location.x, item.location.y);
    }
    function zoomer(level) { return function(item) { zoom(item, level); }; }
    function fillMunicipality(item) {
      self.search(", " + loc(["municipality", item.municipality]));
      $("#copy-search").caretToStart();
    }
    function fillAddress(item) {
      self.search(item.street + " " + item.number + ", " + loc(["municipality", item.municipality]));
      var addressEndIndex = item.street.length + item.number.toString().length + 1;
      $("#copy-search").caretTo(addressEndIndex);
    }

    function selector(item) { return function(value) { return _.every(value[0], function(v, k) { return item[k] === v; }); }; }
    function toHandler(value) { return value[1]; }
    function invoker(item) { return function(handler) { return handler(item); }; }

    var handlers = [
      [{kind: "poi"}, comp(zoom, fillMunicipality)],
      [{kind: "address"}, comp(fillAddress, self.searchNow)],
      [{kind: "address", type: "street"}, zoomer(13)],
      [{kind: "address", type: "street-city"}, zoomer(13)],
      [{kind: "address", type: "street-number"}, zoomer(14)],
      [{kind: "address", type: "street-number-city"}, zoomer(14)],
      [{kind: "property-id"}, comp(zoomer(14), self.searchNow)]
    ];

    var renderers = [
      [{kind: "poi"}, function(item) {
        return $("<a>")
          .addClass("create-find") // Todo: copy-find
          .addClass("poi")
          .append($("<span>").addClass("name").text(item.text))
          .append($("<span>").addClass("municipality").text(loc(["municipality", item.municipality])))
          .append($("<span>").addClass("type").text(loc(["poi.type", item.type])));
      }],
      [{kind: "address"}, function(item) {
        var a = $("<a>")
          .addClass("create-find") // Todo: copy-find
          .addClass("address")
          .append($("<span>").addClass("street").text(item.street));
        if (item.number) {
          a.append($("<span>").addClass("number").text(item.number));
        }
        if (item.municipality) {
          a.append($("<span>").addClass("municipality").text(loc(["municipality", item.municipality])));
        }
        return a;
      }],
      [{kind: "property-id"}, function(item) {
        return $("<a>")
          .addClass("create-find") // Todo: copy-find
          .addClass("property-id")
          .append($("<span>").text(util.prop.toHumanFormat(item["property-id"])));
      }]
    ];

    self.autocompleteSelect = function(e, data) {
      var item = data.item;
      _(handlers).filter(selector(item)).map(toHandler).each(invoker(item));
      return false;
    };

    self.autocompleteRender = function(ul, data) {
      var element = _(renderers).filter(selector(data)).take(1).map(toHandler).map(invoker(data)).value();
      return $("<li>")
        .append(element)
        .appendTo(ul);
    };

    self.updateOrganizationDetails = function(operation) {
      if (self.locationModel.municipalityCode() && operation) {
        ajax
          .query("organization-details", {
            municipality: self.locationModel.municipalityCode(),
            operation: operation,
            lang: loc.getCurrentLanguage()
          })
          .success(function(d) {
            self.newApplicationsDisabled(d["new-applications-disabled"]);
            self.organization(d);
          })
          .error(function() {
            // TODO display error message?
            self.newApplicationsDisabled(true);
          })
          .call();
      }
    };

    self.copyApplication = function() {
      var op = self.operation();
      var params = self.locationModel.toJS();
      params["source-application-id"] = self.sourceApplicationId(); // TODO

      ajax.command("copy-application", params)
        .processing(self.processing)
        .pending(self.pending)
        .success(function(data) {
          self.clear();
          params.id = data.id;
          pageutil.openApplicationPage(params);
        })
        .call();
      hub.send("track-click", {category:"Copy", label:"tree", event:"copyApplication"});
    };
  }

  var model = new CopyApplicationModel();

  hub.onPageLoad("copy-part-1", model.clear);

  function initAutocomplete(id) {
    $(id)
      .keypress(function(e) { if (e.which === 13) { model.searchNow(); }}) // enter
      .autocomplete({
        source:     "/proxy/find-address?lang=" + loc.getCurrentLanguage(),
        delay:      500,
        minLength:  3,
        select:     model.autocompleteSelect
      })
      .data("ui-autocomplete")._renderItem = model.autocompleteRender;
  }

  $(function() {
    $("#copy-part-1").applyBindings(model);

    initAutocomplete("#copy-search");

  });

})();
