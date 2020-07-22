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
package com.axelor.base.service.ical;

import com.axelor.apps.base.db.ICalendarEvent;
import com.axelor.apps.base.db.ICalendarUser;
import com.axelor.apps.base.db.repo.ICalendarEventRepository;
import com.axelor.apps.base.ical.ICalendarException;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.tool.date.DurationTool;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import javax.mail.MessagingException;
import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException;
import org.dmfs.rfc5545.recur.RecurrenceRule;
import org.dmfs.rfc5545.recurrenceset.RecurrenceList;
import org.dmfs.rfc5545.recurrenceset.RecurrenceRuleAdapter;
import org.dmfs.rfc5545.recurrenceset.RecurrenceSet;
import org.dmfs.rfc5545.recurrenceset.RecurrenceSetIterator;

public class ICalendarEventServiceImpl implements ICalendarEventService {

  @Inject protected UserRepository userRepository;

  @Inject protected ICalendarEventRepository iCalEventRepo;

  @Override
  public List<ICalendarUser> addEmailGuest(EmailAddress email, ICalendarEvent event)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException,
          AxelorException, MessagingException, IOException, ICalendarException, ParseException {
    if (email != null) {
      if (event.getAttendees() == null
          || !event
              .getAttendees()
              .stream()
              .anyMatch(x -> email.getAddress().equals(x.getEmail()))) {
        ICalendarUser calUser = new ICalendarUser();
        calUser.setEmail(email.getAddress());
        calUser.setName(email.getName());
        if (email.getPartner() != null) {
          calUser.setUser(
              userRepository
                  .all()
                  .filter("self.partner.id = ?1", email.getPartner().getId())
                  .fetchOne());
        }
        event.addAttendee(calUser);
      }
    }
    return event.getAttendees();
  }

  @Override
  public void generatRecurrentEvents(ICalendarEvent iCalEvent) {

    if (iCalEvent.getRecurrenceConfiguration() == null
        || ObjectUtils.isEmpty(iCalEvent.getRecurrenceConfiguration().getRecurrenceRule())) {
      return;
    }

    try {
      RecurrenceRule rule =
          new RecurrenceRule(iCalEvent.getRecurrenceConfiguration().getRecurrenceRule());
      LocalDateTime startDateT = iCalEvent.getStartDateTime();
      RecurrenceSet recSet = new RecurrenceSet();
      recSet.addInstances(new RecurrenceRuleAdapter(rule));
      // Skip the startDateT of iCalEvent
      recSet.addExceptions(
          new RecurrenceList(
              new long[] {startDateT.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()}));

      DateTime start =
          new DateTime(
              TimeZone.getTimeZone(ZoneId.systemDefault()),
              startDateT.getYear(),
              startDateT.getMonthValue() - 1,
              startDateT.getDayOfMonth(),
              startDateT.getHour(),
              startDateT.getMinute(),
              startDateT.getSecond());

      long durationInSecs =
          DurationTool.getSecondsDuration(
              DurationTool.computeDuration(startDateT, iCalEvent.getEndDateTime()));
      RecurrenceSetIterator iterator = recSet.iterator(start.getTimeZone(), start.getTimestamp());

      // TODO to make this dynamic
      int maxInstances = 100;
      ICalendarEvent lastEvent = iCalEvent;
      while (iterator.hasNext() && maxInstances-- > 0) {
        long nextMillis = iterator.next();
        LocalDateTime nextStartDateTime =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(nextMillis), ZoneId.systemDefault());
        lastEvent = generatRecurrentEvent(lastEvent, nextStartDateTime, durationInSecs);
      }

    } catch (InvalidRecurrenceRuleException e) {
      TraceBackService.trace(e);
    }
  }

  @Transactional
  protected ICalendarEvent generatRecurrentEvent(
      ICalendarEvent source, LocalDateTime nextStartDateTime, long durationInSecs) {
    ICalendarEvent copy = iCalEventRepo.copy(source, false);
    copy.setParentEvent(source);
    copy.setStartDateTime(nextStartDateTime);
    copy.setEndDateTime(nextStartDateTime.plusSeconds(durationInSecs));
    return iCalEventRepo.save(copy);
  }

  @Override
  @Transactional
  public void applyChangesToAll(ICalendarEvent event) {
    applyChangesToAfterThis(event);
    applyChangesToBeforeThis(event);
  }

  @Override
  @Transactional
  public void applyChangesToAfterThis(ICalendarEvent event) {
    ICalendarEvent child =
        iCalEventRepo.all().filter("self.parentEvent.id = ?1", event.getId()).fetchOne();
    while (child != null) {
      event = copyChanges(event, child);
      child = iCalEventRepo.all().filter("self.parentEvent.id = ?1", event.getId()).fetchOne();
    }
  }

  @Override
  @Transactional
  public void applyChangesToBeforeThis(ICalendarEvent event) {
    ICalendarEvent parent = event.getParentEvent();
    while (parent != null) {
      ICalendarEvent nextParent = parent.getParentEvent();
      copyChanges(event, parent);
      parent = nextParent;
    }
  }

  @Override
  @Transactional
  public void removeAll(ICalendarEvent event) {
    removeBeforeThis(event);
    removeThisAndAfterThis(event);
  }

  @Override
  @Transactional
  public void removeThis(ICalendarEvent event) {
    ICalendarEvent child =
        iCalEventRepo.all().filter("self.parentEvent.id = ?1", event.getId()).fetchOne();
    if (child != null) {
      child.setParentEvent(event.getParentEvent());
    }
    iCalEventRepo.remove(event);
  }

  @Override
  @Transactional
  public void removeThisAndAfterThis(ICalendarEvent event) {
    ICalendarEvent eventToDelete = event;
    while (eventToDelete != null) {
      ICalendarEvent child =
          iCalEventRepo.all().filter("self.parentEvent.id = ?1", eventToDelete.getId()).fetchOne();
      removeThis(eventToDelete);
      eventToDelete = child;
    }
  }

  @Override
  @Transactional
  public void removeBeforeThis(ICalendarEvent event) {
    ICalendarEvent parent = event.getParentEvent();
    while (parent != null) {
      ICalendarEvent nextParent = parent.getParentEvent();
      iCalEventRepo.remove(parent);
      parent = nextParent;
    }
  }

  @Transactional
  protected ICalendarEvent copyChanges(ICalendarEvent source, ICalendarEvent target) {
    target.setSubject(source.getSubject());
    target.setCalendar(source.getCalendar());
    // target.setStartDateTime(source.getStartDateTime());
    // target.setEndDateTime(source.getEndDateTime());
    //			parent.setDuration(event.getDuration());
    target.setUser(source.getUser());
    //			parent.setTeam(event.getTeam());
    target.setDisponibilitySelect(source.getDisponibilitySelect());
    target.setVisibilitySelect(source.getVisibilitySelect());
    target.setDescription(source.getDescription());
    //			parent.setPartner(event.getPartner());
    //			parent.setContactPartner(event.getContactPartner());
    //			parent.setLead(event.getLead());
    target.setTypeSelect(source.getTypeSelect());
    target.setLocation(source.getLocation());
    return iCalEventRepo.save(target);
  }
}
