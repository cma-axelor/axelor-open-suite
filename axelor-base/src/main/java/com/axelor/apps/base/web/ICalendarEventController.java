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
package com.axelor.apps.base.web;

import com.axelor.apps.base.db.ICalendarEvent;
import com.axelor.apps.base.db.RecurrenceConfiguration;
import com.axelor.apps.base.db.repo.ICalendarEventRepository;
import com.axelor.apps.base.db.repo.RecurrenceConfigurationRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.ical.ICalendarException;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.base.service.ical.ICalendarEventService;
import com.axelor.base.service.ical.RecurrenceConfigurationService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.io.IOException;
import java.text.ParseException;
import java.util.Map;
import javax.mail.MessagingException;

public class ICalendarEventController {

  @SuppressWarnings("unchecked")
  public void addEmailGuest(ActionRequest request, ActionResponse response)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException,
          AxelorException, MessagingException, IOException, ICalendarException, ParseException {
    ICalendarEvent event = request.getContext().asType(ICalendarEvent.class);
    try {
      Map<String, Object> guestEmail = (Map<String, Object>) request.getContext().get("guestEmail");
      if (guestEmail != null) {
        EmailAddress emailAddress =
            Beans.get(EmailAddressRepository.class)
                .find(new Long((guestEmail.get("id").toString())));
        if (emailAddress != null) {
          response.setValue(
              "attendees",
              Beans.get(ICalendarEventService.class).addEmailGuest(emailAddress, event));
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void generateRecurrentEvents(ActionRequest request, ActionResponse response)
      throws AxelorException {
    try {
      Long eventId = (Long) request.getContext().get("id");

      if (eventId == null)
        throw new AxelorException(
            ICalendarEvent.class,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.EVENT_SAVED));

      ICalendarEvent event = Beans.get(ICalendarEventRepository.class).find(eventId);

      if (event.getRecurrenceConfiguration() != null) {
        Beans.get(ICalendarEventService.class).generatRecurrentEvents(event);
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void deleteThis(ActionRequest request, ActionResponse response) {
    Long eventId = new Long(request.getContext().getParent().get("id").toString());
    ICalendarEventRepository iCalEventRepo = Beans.get(ICalendarEventRepository.class);
    ICalendarEvent event = iCalEventRepo.find(eventId);
    Beans.get(ICalendarEventService.class).removeThis(event);
    response.setReload(true);
  }

  public void deleteNext(ActionRequest request, ActionResponse response) {
    Long eventId = new Long(request.getContext().getParent().get("id").toString());
    ICalendarEventRepository iCalEventRepo = Beans.get(ICalendarEventRepository.class);
    ICalendarEventService iCalEventService = Beans.get(ICalendarEventService.class);
    iCalEventService.removeThisAndAfterThis(iCalEventRepo.find(eventId));
    response.setCanClose(true);
  }

  public void deleteAll(ActionRequest request, ActionResponse response) {
    Long eventId = new Long(request.getContext().getParent().get("id").toString());
    ICalendarEventRepository iCalEventRepo = Beans.get(ICalendarEventRepository.class);
    ICalendarEvent event = iCalEventRepo.find(eventId);
    Beans.get(ICalendarEventService.class).removeAll(event);
    response.setCanClose(true);
  }

  public void changeAll(ActionRequest request, ActionResponse response) throws AxelorException {
    Long eventId = new Long(request.getContext().getParent().get("id").toString());
    ICalendarEventRepository iCalEventRepo = Beans.get(ICalendarEventRepository.class);
    ICalendarEvent event = iCalEventRepo.find(eventId);

    Beans.get(ICalendarEventService.class).removeAll(event);

    RecurrenceConfiguration conf = request.getContext().asType(RecurrenceConfiguration.class);
    conf = Beans.get(RecurrenceConfigurationRepository.class).find(conf.getId());
    if (!conf.equals(event.getRecurrenceConfiguration())) {
      event.setRecurrenceConfiguration(conf);
    }
    Beans.get(ICalendarEventService.class).generatRecurrentEvents(event);

    response.setCanClose(true);
  }

  public void applyChangesToAll(ActionRequest request, ActionResponse response) {
    ICalendarEventRepository iCalEventRepo = Beans.get(ICalendarEventRepository.class);
    ICalendarEvent event =
        iCalEventRepo.find(new Long(request.getContext().get("_idEvent").toString()));
    Beans.get(ICalendarEventService.class).applyChangesToAll(event);
    response.setCanClose(true);
  }

  public void computeRecurrenceName(ActionRequest request, ActionResponse response) {
    RecurrenceConfiguration recurrConf = request.getContext().asType(RecurrenceConfiguration.class);
    response.setValue(
        "recurrenceName",
        Beans.get(RecurrenceConfigurationService.class).computeRecurrenceName(recurrConf));
  }
}
