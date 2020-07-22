/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.crm.web;

import com.axelor.apps.base.service.MapService;
import com.axelor.apps.crm.db.Event;
import com.axelor.apps.crm.db.EventReminder;
import com.axelor.apps.crm.db.Lead;
import com.axelor.apps.crm.db.repo.EventReminderRepository;
import com.axelor.apps.crm.db.repo.EventRepository;
import com.axelor.apps.crm.db.repo.LeadRepository;
import com.axelor.apps.crm.exception.IExceptionMessage;
import com.axelor.apps.crm.service.CalendarService;
import com.axelor.apps.crm.service.EventService;
import com.axelor.apps.crm.service.LeadService;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.tool.date.DateTool;
import com.axelor.apps.tool.date.DurationTool;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.base.service.ical.ICalendarEventService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Joiner;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EventController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public void computeFromStartDateTime(ActionRequest request, ActionResponse response) {

    Event event = request.getContext().asType(Event.class);

    LOG.debug("event : {}", event);

    if (event.getStartDateTime() != null) {
      if (event.getDuration() != null && event.getDuration() != 0) {
        response.setValue(
            "endDateTime", DateTool.plusSeconds(event.getStartDateTime(), event.getDuration()));
      } else if (event.getEndDateTime() != null
          && event.getEndDateTime().isAfter(event.getStartDateTime())) {
        Duration duration =
            DurationTool.computeDuration(event.getStartDateTime(), event.getEndDateTime());
        response.setValue("duration", DurationTool.getSecondsDuration(duration));
      } else {
        Duration duration = Duration.ofHours(1);
        response.setValue("duration", DurationTool.getSecondsDuration(duration));
        response.setValue("endDateTime", event.getStartDateTime().plus(duration));
      }
    }
  }

  public void computeFromEndDateTime(ActionRequest request, ActionResponse response) {

    Event event = request.getContext().asType(Event.class);

    LOG.debug("event : {}", event);

    if (event.getEndDateTime() != null) {
      if (event.getStartDateTime() != null
          && event.getStartDateTime().isBefore(event.getEndDateTime())) {
        Duration duration =
            DurationTool.computeDuration(event.getStartDateTime(), event.getEndDateTime());
        response.setValue("duration", DurationTool.getSecondsDuration(duration));
      } else if (event.getDuration() != null) {
        response.setValue(
            "startDateTime", DateTool.minusSeconds(event.getEndDateTime(), event.getDuration()));
      }
    }
  }

  public void computeFromDuration(ActionRequest request, ActionResponse response) {

    Event event = request.getContext().asType(Event.class);

    LOG.debug("event : {}", event);

    if (event.getDuration() != null) {
      if (event.getStartDateTime() != null) {
        response.setValue(
            "endDateTime", DateTool.plusSeconds(event.getStartDateTime(), event.getDuration()));
      } else if (event.getEndDateTime() != null) {
        response.setValue(
            "startDateTime", DateTool.minusSeconds(event.getEndDateTime(), event.getDuration()));
      }
    }
  }

  public void computeFromCalendar(ActionRequest request, ActionResponse response) {

    Event event = request.getContext().asType(Event.class);

    LOG.debug("event : {}", event);

    if (event.getStartDateTime() != null && event.getEndDateTime() != null) {
      Duration duration =
          DurationTool.computeDuration(event.getStartDateTime(), event.getEndDateTime());
      response.setValue("duration", DurationTool.getSecondsDuration(duration));
    }
  }

  public void saveEventTaskStatusSelect(ActionRequest request, ActionResponse response)
      throws AxelorException {

    Event event = request.getContext().asType(Event.class);
    Event persistEvent = Beans.get(EventRepository.class).find(event.getId());
    persistEvent.setStatusSelect(event.getStatusSelect());
    Beans.get(EventService.class).saveEvent(persistEvent);
  }

  public void saveEventTicketStatusSelect(ActionRequest request, ActionResponse response)
      throws AxelorException {

    Event event = request.getContext().asType(Event.class);
    Event persistEvent = Beans.get(EventRepository.class).find(event.getId());
    persistEvent.setStatusSelect(event.getStatusSelect());
    Beans.get(EventService.class).saveEvent(persistEvent);
  }

  public void viewMap(ActionRequest request, ActionResponse response) {
    try {
      Event event = request.getContext().asType(Event.class);
      if (event.getLocation() != null) {
        Map<String, Object> result = Beans.get(MapService.class).getMap(event.getLocation());
        if (result != null) {
          Map<String, Object> mapView = new HashMap<>();
          mapView.put("title", "Map");
          mapView.put("resource", result.get("url"));
          mapView.put("viewType", "html");
          response.setView(mapView);
        } else
          response.setFlash(
              String.format(
                  I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.ADDRESS_5),
                  event.getLocation()));
      } else response.setFlash(I18n.get(IExceptionMessage.EVENT_1));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  @SuppressWarnings("rawtypes")
  public void assignToMeLead(ActionRequest request, ActionResponse response) {

    LeadService leadService = Beans.get(LeadService.class);
    LeadRepository leadRepo = Beans.get(LeadRepository.class);

    if (request.getContext().get("id") != null) {
      Lead lead = leadRepo.find((Long) request.getContext().get("id"));
      lead.setUser(AuthUtils.getUser());
      if (lead.getStatusSelect() == LeadRepository.LEAD_STATUS_NEW)
        lead.setStatusSelect(LeadRepository.LEAD_STATUS_ASSIGNED);
      leadService.saveLead(lead);
    } else if (((List) request.getContext().get("_ids")) != null) {
      for (Lead lead :
          leadRepo.all().filter("id in ?1", request.getContext().get("_ids")).fetch()) {
        lead.setUser(AuthUtils.getUser());
        if (lead.getStatusSelect() == LeadRepository.LEAD_STATUS_NEW)
          lead.setStatusSelect(LeadRepository.LEAD_STATUS_ASSIGNED);
        leadService.saveLead(lead);
      }
    }
    response.setReload(true);
  }

  @SuppressWarnings("rawtypes")
  public void assignToMeEvent(ActionRequest request, ActionResponse response) {

    EventRepository eventRepository = Beans.get(EventRepository.class);

    if (request.getContext().get("id") != null) {
      Event event = eventRepository.find((Long) request.getContext().get("id"));
      event.setUser(AuthUtils.getUser());
      Beans.get(EventService.class).saveEvent(event);
    } else if (!((List) request.getContext().get("_ids")).isEmpty()) {
      for (Event event :
          eventRepository.all().filter("id in ?1", request.getContext().get("_ids")).fetch()) {
        event.setUser(AuthUtils.getUser());
        Beans.get(EventService.class).saveEvent(event);
      }
    }
    response.setReload(true);
  }

  public void manageFollowers(ActionRequest request, ActionResponse response)
      throws AxelorException {
    try {
      Event event = request.getContext().asType(Event.class);
      event = Beans.get(EventRepository.class).find(event.getId());
      Beans.get(EventService.class).manageFollowers(event);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void checkRights(ActionRequest request, ActionResponse response) {
    Event event = request.getContext().asType(Event.class);
    User user = AuthUtils.getUser();
    List<Long> calendarIdlist = Beans.get(CalendarService.class).showSharedCalendars(user);
    if (calendarIdlist.isEmpty() || !calendarIdlist.contains(event.getCalendar().getId())) {
      response.setAttr("meetingGeneralPanel", "readonly", "true");
      response.setAttr("addGuestsPanel", "readonly", "true");
      response.setAttr("meetingAttributesPanel", "readonly", "true");
      response.setAttr("meetingLinkedPanel", "readonly", "true");
    }
  }

  public void changeCreator(ActionRequest request, ActionResponse response) {
    User user = AuthUtils.getUser();
    response.setValue("organizer", Beans.get(CalendarService.class).findOrCreateUser(user));
  }

  /**
   * This method is used to add attendees/guests from partner or contact partner or lead
   *
   * @param request
   * @param response
   */
  public void addGuest(ActionRequest request, ActionResponse response) {
    Event event = request.getContext().asType(Event.class);
    try {
      EmailAddress emailAddress = Beans.get(EventService.class).getEmailAddress(event);
      if (emailAddress != null) {
        response.setValue(
            "attendees", Beans.get(ICalendarEventService.class).addEmailGuest(emailAddress, event));
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  public void deleteReminder(ActionRequest request, ActionResponse response) {
    try {
      EventReminderRepository eventReminderRepository = Beans.get(EventReminderRepository.class);

      EventReminder eventReminder =
          eventReminderRepository.find((long) request.getContext().get("id"));
      eventReminderRepository.remove(eventReminder);
      response.setCanClose(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void setCalendarDomain(ActionRequest request, ActionResponse response) {
    User user = AuthUtils.getUser();
    List<Long> calendarIdlist = Beans.get(CalendarService.class).showSharedCalendars(user);
    if (calendarIdlist.isEmpty()) {
      response.setAttr("calendar", "domain", "self.id is null");
    } else {
      response.setAttr(
          "calendar", "domain", "self.id in (" + Joiner.on(",").join(calendarIdlist) + ")");
    }
  }
}
