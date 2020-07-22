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
package com.axelor.apps.crm.service;

import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.ICalendarUser;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.ical.ICalendarService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.crm.db.Event;
import com.axelor.apps.crm.db.Lead;
import com.axelor.apps.crm.db.repo.EventRepository;
import com.axelor.apps.crm.db.repo.LeadRepository;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.message.service.MessageService;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.auth.db.User;
import com.axelor.inject.Beans;
import com.axelor.mail.db.MailAddress;
import com.axelor.mail.db.MailFollower;
import com.axelor.mail.db.repo.MailAddressRepository;
import com.axelor.mail.db.repo.MailFollowerRepository;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class EventServiceImpl implements EventService {

  private PartnerService partnerService;

  private EventRepository eventRepo;

  @Inject private EmailAddressRepository emailAddressRepo;

  @Inject private PartnerRepository partnerRepo;

  @Inject private LeadRepository leadRepo;

  @Inject
  public EventServiceImpl(
      EventAttendeeService eventAttendeeService,
      PartnerService partnerService,
      EventRepository eventRepository,
      MailFollowerRepository mailFollowerRepo,
      ICalendarService iCalendarService,
      MessageService messageService,
      TemplateMessageService templateMessageService) {
    this.partnerService = partnerService;
    this.eventRepo = eventRepository;
  }

  @Override
  @Transactional
  public void saveEvent(Event event) {
    eventRepo.save(event);
  }

  @Override
  public Event createEvent(
      LocalDateTime fromDateTime,
      LocalDateTime toDateTime,
      User user,
      String description,
      int type,
      String subject) {
    Event event = new Event();
    event.setSubject(subject);
    event.setStartDateTime(fromDateTime);
    event.setEndDateTime(toDateTime);
    event.setUser(user);
    event.setTypeSelect(type);
    if (!Strings.isNullOrEmpty(description)) {
      event.setDescription(description);
    }

    if (fromDateTime != null && toDateTime != null) {
      long duration = Duration.between(fromDateTime, toDateTime).getSeconds();
      event.setDuration(duration);
    }

    return event;
  }

  @Override
  public String getInvoicingAddressFullName(Partner partner) {

    Address address = partnerService.getInvoicingAddress(partner);
    if (address != null) {
      return address.getFullName();
    }

    return null;
  }

  @Override
  @Transactional
  public void manageFollowers(Event event) {
    MailFollowerRepository mailFollowerRepo = Beans.get(MailFollowerRepository.class);
    List<MailFollower> followers = mailFollowerRepo.findAll(event);
    List<ICalendarUser> attendeesSet = event.getAttendees();

    if (followers != null) followers.forEach(x -> mailFollowerRepo.remove(x));
    mailFollowerRepo.follow(event, event.getUser());

    if (attendeesSet != null) {
      for (ICalendarUser user : attendeesSet) {
        if (user.getUser() != null) {
          mailFollowerRepo.follow(event, user.getUser());
        } else {
          MailAddress mailAddress =
              Beans.get(MailAddressRepository.class).findOrCreate(user.getEmail(), user.getName());
          mailFollowerRepo.follow(event, mailAddress);
        }
      }
    }
  }

  @Override
  public EmailAddress getEmailAddress(Event event) {
    EmailAddress emailAddress = null;
    if (event.getPartner() != null
        && event.getPartner().getPartnerTypeSelect() == PartnerRepository.PARTNER_TYPE_INDIVIDUAL) {

      Partner partner = partnerRepo.find(event.getPartner().getId());
      if (partner.getEmailAddress() != null)
        emailAddress = emailAddressRepo.find(partner.getEmailAddress().getId());

    } else if (event.getContactPartner() != null) {

      Partner contactPartner = partnerRepo.find(event.getContactPartner().getId());
      if (contactPartner.getEmailAddress() != null)
        emailAddress = emailAddressRepo.find(contactPartner.getEmailAddress().getId());

    } else if (event.getPartner() == null
        && event.getContactPartner() == null
        && event.getLead() != null) {

      Lead lead = leadRepo.find(event.getLead().getId());
      if (lead.getEmailAddress() != null)
        emailAddress = emailAddressRepo.find(lead.getEmailAddress().getId());
    }
    return emailAddress;
  }
}
