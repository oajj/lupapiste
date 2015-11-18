LUPAPISTE.FileuploadService = function() {
  "use strict";
  var self = this;

  var inputId = "fileupload-input";

  if (!document.getElementById(inputId)) {
    var input = document.createElement("input");
    // input.className = "hidden";
    input.type = "file";
    input.name = "files[]";
    input.setAttribute("multiple", true);
    input.setAttribute("id", inputId);
    document.body.appendChild(input);
  }

  $("#" + inputId).fileupload({
    url: "/upload/file",
    type: "POST",
    dataType: "json",
    done: function(e, data) {
      hub.send("fileuploadService::filesUploaded", {files: data.result.files});
    }
  });

  hub.subscribe("fileuploadService::uploadFile", function() {
    $("#fileupload-input").click();
  });
};
