(function() {
  "use strict";

  function UserReport() {
    var self = this;

    self.options = [{value: "yes", text: "Kyllä"},
                    {value: "no", text: "Ei"},
                    {value: "both", text: "Sekä että"}];
    self.values = [{value: ko.observable(), label: "Yritystili",
                    arg: "company"},
                   {value: ko.observable(), label: "Ammattilainen",
                    arg: "professional"},
                   {value: ko.observable(), label: "Suoramarkkinointilupa",
                    arg: "allow"}];

    
    self.link = ko.pureComputed( function() {
      return "/api/raw/user-report?"
           + _(self.values)
             .map( function( v ) {
               return sprintf( "%s=%s", v.arg, v.value())
             })
             .join( "&");
    });
  }

  $(function() {
    $("#admin-user-report").applyBindings( new UserReport());
  });

})();
