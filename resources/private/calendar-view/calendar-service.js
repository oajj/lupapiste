LUPAPISTE.CalendarService = function() {
  "use strict";
  var self = this;

  self.calendar = ko.observable();
  self.calendarWeekdays = ko.observableArray();

  self.calendarQuery = {};

  var doFetchCalendarSlots = function(event) {
    ajax.query("calendar-slots", {calendarId: event.id, week: event.week, year: event.year})
      .success(function(data) {
        var startOfWeek = moment().isoWeek(event.week).year(event.year).startOf('isoWeek').valueOf();
        var weekdays = _.map([1, 2, 3, 4, 5], function(i) {
          var day = moment(startOfWeek).isoWeekday(i);
          var slotsForDay = _.filter(data.slots, function(s) { return day.isSame(s.startTime, 'day'); });
          return {
            calendarId: event.id,
            startOfDay: day.valueOf(),
            str: day.format("DD.MM."),  // TODO -> ko.bindingHandlers.calendarViewDateHeader?
            slots: _.map(slotsForDay,
              function(s) {
                return _.extend(s, { duration: moment(s.endTime).diff(s.startTime) });
              })};
        });
        self.calendarWeekdays(weekdays);
      })
      .call();
  };

  var _fetchCalendar = hub.subscribe("calendarService::fetchCalendar", function(event) {
    ajax.query("calendar", {calendarId: event.id})
      .success(function(data) {
        self.calendar(data.calendar);
        doFetchCalendarSlots({id: data.calendar.id, week: moment().isoWeek(), year: moment().year()});
      })
      .call();
  });

  var _fetchSlots = hub.subscribe("calendarService::fetchCalendarSlots", function(event) {
    doFetchCalendarSlots(event);
  });

  var _createSlots = hub.subscribe("calendarService::createCalendarSlots", function(event) {
    ajax
      .command("create-calendar-slots", {calendarId: event.calendarId, slots: event.slots})
      .success(function() {
        if (event.modalClose) {
          LUPAPISTE.ModalDialog.close();
        }
        doFetchCalendarSlots({id: data.calendar.id, week: moment().isoWeek(), year: moment().year()});
      })
      .call();
  });

  self.dispose = function() {
    hub.unsubscribe(_fetchCalendar);
    hub.unsubscribe(_createSlots);
  };
};