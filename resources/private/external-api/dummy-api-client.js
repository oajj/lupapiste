/**
* -- FOR DEVELOPMENT USE ONLY --
* Mimics API implemented by 3rd parties.
* Outputs data in modal dialog for debugging purposes.
* @constructs LupapisteApi
*/
function LupapisteApi() {

}

/**
* Show permits on map by a filter
* @static
* @param {Array<PermitFilter>} permits Permits from Lupapiste view
*/
LupapisteApi.showPermitsOnMap = function (permits) {
  hub.send("show-dialog", {title: "LupapisteApi.showPermitsOnMap",
                           component: "ok-dialog",
                           componentParams: {text: JSON.stringify(permits, null, 2)}});
};

/**
* Show point on map
* @static
* @param {PermitFilter} filter Filter for lupapiste api
*/
LupapisteApi.showPermitOnMap = function (permit) {
  hub.send("show-dialog", {title: "LupapisteApi.showPermitOnMap",
                           component: "ok-dialog",
                           componentParams: {text: JSON.stringify(permit, null, 2)}});
};

/**
* Opens open permit
* @static
* @param {PermitFilter} permit
*/
LupapisteApi.openPermit = function (permit) {
  hub.send("show-dialog", {title: "LupapisteApi.openPermit",
                           component: "ok-dialog",
                           componentParams: {text: JSON.stringify(permit, null, 2)}});
};

/**
* Permit is emited when integration (KRYSP) was sent successfully
* @static
* @param {PermitFilter} permit
*/
LupapisteApi.integrationSent = function (permit) {
  hub.send("show-dialog", {title: "LupapisteApi.integrationSent",
                           component: "ok-dialog",
                           componentParams: {text: JSON.stringify(permit, null, 2)}});
};

/**
* Queries SitoGis if the permit is there
* @static
* @param {string} id Permit id (asiointitunnus)
* @returns {boolean} is the permit in SitoGis?
*/
LupapisteApi.isInSitoGis = function (id) {

};