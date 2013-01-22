;(function() {
  "use strict";

  var isInitializing = true;
  var currentId;
  var applicationModel = new ApplicationModel();
  var authorizationModel = authorization.create();
  var inviteModel = new InviteModel();
  var commentModel = comments.create();
  var applicationMap;
  var inforequestMap;

  function ApplicationModel() {
    var self = this;

    self.data = ko.observable();

    self.auth = ko.computed(function() {
      var value = [];
      if (self.data() !== undefined) {
        var auth = ko.utils.unwrapObservable(self.data().auth());
        var withRoles = function(r, i) {
          var a = r[i.id()] || (i.roles = [], i);
          a.roles.push(i.role());
          r[i.id()] = a;
          return r;
        };
        var pimped = _.reduce(auth, withRoles, {});
        value = _.values(pimped);
      }
      return value;
    }, self);
  }

  var application = {
    id: ko.observable(),
    infoRequest: ko.observable(),
    state: ko.observable(),
    location: ko.observable(),
    permitType: ko.observable(),
    propertyId: ko.observable(),
    title: ko.observable(),
    created: ko.observable(),
    documents: ko.observable(),
    attachments: ko.observableArray(),
    address: ko.observable(),
    verdict: ko.observable(),

    assignee: ko.observable(),

    // new stuff
    invites: ko.observableArray(),

    // all data in here
    data: ko.observable(),

    submitApplication: function(model) {
      var applicationId = application.id();
      ajax.command("submit-application", { id: applicationId})
        .success(function() {
          notify.success("hakemus j\u00E4tetty",model);
          repository.reloadApplication(applicationId);
        })
        .call();
      return false;
    },

    markInforequestAnswered: function(model) {
      var applicationId = application.id();
      ajax.command("mark-inforequest-answered", {id: applicationId})
        .success(function() {
          notify.success("neuvontapyynt\u00F6 merkitty vastatuksi",model);
          repository.reloadApplication(applicationId);
        })
        .call();
      return false;
    },

    convertToApplication: function() {
      var applicationId = application.id();
      ajax.command("convert-to-application", {id: applicationId})
        .success(function(d) {
          repository.reloadApplication(applicationId);
          repository.reloadApplication(d.id);
          window.location.hash = "!/application/" + d.id;
        })
        .call();
      return false;
    },

    setMeAsPaasuunnittelija: function(model) {
      var applicationId = application.id();
      ajax.command("user-to-document", { id: applicationId, name: "paasuunnittelija"})
      .success(function() {
        notify.success("tiedot tallennettu",model);
        repository.reloadApplication(applicationId);
      })
      .call();
      return false;
    },

    approveApplication: function(model) {
      var applicationId = application.id();
      ajax.command("approve-application", { id: applicationId})
        .success(function() {
          notify.success("hakemus hyv\u00E4ksytty",model);
          repository.reloadApplication(applicationId);
        })
        .call();
      return false;
    },

    removeInvite: function(model) {
      var applicationId = application.id();
      ajax.command("remove-invite", { id : applicationId, email : model.user.username()})
        .success(function() {
          notify.success("kutsu poistettu", model);
          repository.reloadApplication(applicationId);
        })
        .call();
      return false;
    },

    removeAuth: function(model) {
      var applicationId = application.id();
      ajax.command("remove-auth", { id : applicationId, email : model.username()})
        .success(function() {
          notify.success("oikeus poistettu", model);
          repository.reloadApplication(applicationId);
        })
        .call();
      return false;
    },

    addOperation: function() {
      window.location.hash = "#!/add-operation/" + application.id();
      return false;
    },
    
    cancelApplication: function() {
      var id = application.id();
      ajax
        .command("cancel-application", {id: id})
        .success(function() {
          window.location.hash = "!/applications";
        })
        .call();
      return false;
    }

  };

  var authorities = ko.observableArray([]);
  var attachments = ko.observableArray([]);
  var attachmentsByGroup = ko.observableArray();

  function getAttachmentsByGroup(attachments) {
    var grouped = _.groupBy(attachments, function(attachment) {
      return attachment.type['type-group'];
    });
    var result = _.map(grouped, function(value, key) {
      return {group: key, attachments: value};
    });
    return result;
  }

  var AuthorityInfo = function(id, firstName, lastName) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
  };

  function updateAssignee(value) {
    debug("updateAssignee called, assigneeId: ", value);
    // do not update assignee if page is still initializing
    if (isInitializing) {
      debug("isInitializing, return");
      return;
    }

    // The right is validated in the back-end. This check is just to prevent error.
    if (!authorizationModel.ok('assign-application')) {
      return;
    }

    var assigneeId = value ? value : null;

    debug("Setting application " + currentId + " assignee to " + assigneeId);
    ajax.command("assign-application", {id: currentId, assigneeId: assigneeId})
      .success(function() {
      })
      .error(function(e) {
        error(e);
      })
      .fail(function(e) {
        error(e);
      }).call();
  }

  application.assignee.subscribe(function(v) { updateAssignee(v); });

  function resolveApplicationAssignee(roles) {
    debug("resolveApplicationAssignee called, roles: ", roles);
    if (roles && roles.authority) {
      var auth = new AuthorityInfo(roles.authority.id, roles.authority.firstName, roles.authority.lastName);
      debug("resolved authority: ", auth);
      return auth;
    } else {
      debug("not assigned");
      return null;
    }
  }

  function initAuthoritiesSelectList(data) {
    authorities.removeAll();
    _.each(data || [], function(authority) {
      authorities.push(new AuthorityInfo(authority.id, authority.firstName, authority.lastName));
    });
  }

  function showApplication(applicationDetails) {
    debug("set isInitializing to true");
    isInitializing = true;
    debug("showApplication called", applicationDetails);
    authorizationModel.refresh(applicationDetails.application,function() {
      // new data mapping
      var app = applicationDetails.application;
      applicationModel.data(ko.mapping.fromJS(app));
      ko.mapping.fromJS(app, {}, application);

      // comments
      commentModel.setApplicationId(app.id);
      commentModel.setComments(app.comments);

      var statuses = {
        requires_user_action: {statusName: "missing"},
        requires_authority_action: {statusName: "new"},
        ok:{statusName: "ok"}
      };

      attachments.removeAll();
      _.each(app.attachments || [], function(a) {
        var s = statuses[a.state];
        a.statusName = s.statusName || "unknown";
        attachments.push(a);
      });

      attachmentsByGroup(getAttachmentsByGroup(app.attachments));

      initAuthoritiesSelectList(applicationDetails.authorities);

      // Update map:
      var location = application.location();
      var x = location.x();
      var y = location.y();
      applicationMap.clear().add(x, y).center(x, y, 10);
      inforequestMap.clear().add(x, y).center(x, y, 10);

      // docgen:

      var save = function(path, value, callback, data) {
        ajax
          .command("update-doc", {doc: data.doc, id: data.app, updates: [[path, value]]})
          // Server returns empty array (all ok), or array containing an array with three
          // elements: [key status message]. Here we use just the status.
          .success(function(e) {
            var status = (e.results.length === 0) ? "ok" : e.results[0][1];
            callback(status);
          })
          .error(function(e) { error(e); callback("err"); })
          .fail(function(e) { error(e); callback("err"); })
          .call();
      };

      function displayDocuments(containerSelector, documents) {

        var groupedDocs = _.groupBy(documents, function (doc) {
          return doc.schema.info.name;
      });

        var displayOrder = ["rakennuspaikka", "uusiRakennus", "lisatiedot", "hakija", "paasuunnittelija", "suunnittelija", "maksaja"];
        var sortedDocs = _.sortBy(groupedDocs, function (docGroup) {
          return _.indexOf(displayOrder, docGroup[0].schema.info.name)
        });

        var docgenDiv = $(containerSelector).empty();
        _.each(sortedDocs, function(docGroup) {
          _.each(docGroup, function(doc) {
            docgenDiv.append(new LUPAPISTE.DocModel(doc.schema, doc.body, save, doc.id, application.id()).element);
          });

          var schema = docGroup[0].schema;

          if (schema.info.repeating) {
            var btn = LUPAPISTE.DOMUtils.makeButton(schema.info.name + "_append_btn", loc(schema.info.name + "._append_label"));

            $(btn).click(function() {
              var self = this;
              ajax
                .command("create-doc", {schema: schema.info.name, id: application.id()})
                .success(function(data) {
                  var newDocId = data.doc;
                  var newElem = new LUPAPISTE.DocModel(schema, {}, save, newDocId, application.id()).element;
                  $(self).before(newElem);
                })
                .call();
            });
            docgenDiv.append(btn);
          }
        });
      }

      var partyDocumentNames = ["hakija", "paasuunnittelija", "suunnittelija", "maksaja"];
      displayDocuments("#applicationDocgen", _.filter(app.documents, function(doc) {return !_.contains(partyDocumentNames, doc.schema.info.name);}));
      displayDocuments("#partiesDocgen", _.filter(app.documents, function(doc) {return _.contains(partyDocumentNames, doc.schema.info.name);}));

      if(! isTabSelected('#applicationTabs')) {
        selectDefaultTab('#applicationTabs');
      }

      // set the value behind assignee selection list
      var assignee = resolveApplicationAssignee(app.roles);
      var assigneeId = assignee ? assignee.id : null;
      application.assignee(assigneeId);

      debug("set isInitializing to false");
      isInitializing = false;
      pageutil.setPageReady("application");
    });
  }

  hub.subscribe("application-loaded", function(e) {
    if (!currentId || (currentId === e.applicationDetails.application.id)) {
      showApplication(e.applicationDetails);
    }
  });

  function InviteModel() {
    var self = this;

    self.email = ko.observable();
    self.text = ko.observable();

    self.submit = function(model) {
      var email = model.email();
      var text = model.text();
      var id = application.id();
      ajax.command("invite", { id: id,
                               email: email,
                               title: "uuden suunnittelijan lis\u00E4\u00E4minen",
                               text: text})
        .success(function() {
          self.email(undefined);
          self.text(undefined);
          repository.reloadApplication(id);
        })
        .error(function(d) {
          notify.info("kutsun l\u00E4hett\u00E4minen ep\u00E4onnistui",d);
        })
        .call();
      return false;
    };
  }

  var tab = {
    tabClick: function(data, event) {
      var target = event.target;
     setSelectedTab('#applicationTabs', target);
    }
  };

  function isTabSelected(id) {
    return $(id + ' > li').hasClass("active");
  }

  function selectDefaultTab(id) {
    setSelectedTab(id, $('.active-as-default'));
  }

  function setSelectedTab(id, element) {
    $(id + " li").removeClass("active");
    $(element).parent().addClass("active");
    $(".tab-content").hide();
    var selected_tab = $(element).attr("href");
    $(selected_tab).fadeIn();
  }

  var accordian = {
    accordianClick: function(data, event) {
      accordion.toggle(event);
    }
  };

  var initApplication = function(e) {
    currentId = e.pagePath[0];
    applicationMap.updateSize();
    inforequestMap.updateSize();
    hub.send("load-application", {id: currentId});
  };

  hub.onPageChange("application", initApplication);
  hub.onPageChange("inforequest", initApplication);

  $(function() {
    applicationMap = gis.makeMap("application-map").center([{x: 404168, y: 6693765}], 7);
    inforequestMap = gis.makeMap("inforequest-map").center([{x: 404168, y: 6693765}], 7);

    var bindings = {
      application: application,
      authorities: authorities,
      attachments: attachments,
      attachmentsByGroup: attachmentsByGroup,
      applicationModel: applicationModel,
      comment: commentModel,
      invite: inviteModel,
      authorization: authorizationModel,
      tab: tab,
      accordian: accordian
    };

    ko.applyBindings(bindings, $("#application")[0]);
    ko.applyBindings(bindings, $("#inforequest")[0]);
  });

})();
