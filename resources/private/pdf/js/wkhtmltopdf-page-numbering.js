(function() {
  "use strict";
  var pdfInfo = {};
  var x = document.location.search.substring(1).split("&");
  for (var i in x) {
    if (i) {
      var z = x[i].split("=",2);
      pdfInfo[z[0]] = unescape(z[1]);
    }
  }

  var page = pdfInfo.page || 1;
  var pageCount = pdfInfo.topage || 1;

  document.getElementById("page-number").textContent = page;
  document.getElementById("number-of-pages").textContent = pageCount;
})();
