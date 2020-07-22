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

import com.axelor.apps.base.db.RecurrenceConfiguration;
import com.axelor.apps.base.db.repo.RecurrenceConfigurationRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.apache.commons.lang3.StringUtils;
import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.Weekday;
import org.dmfs.rfc5545.recur.Freq;
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException;
import org.dmfs.rfc5545.recur.RecurrenceRule;
import org.dmfs.rfc5545.recur.RecurrenceRule.Part;
import org.dmfs.rfc5545.recur.RecurrenceRule.WeekdayNum;

public class RecurrenceConfigurationServiceImpl implements RecurrenceConfigurationService {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("dd/MM");

  protected static final BiMap<Freq, Integer> RECURRENCE_TYPES_FREQ_MAP;

  static {
    Map<Freq, Integer> temp = new HashMap<>();

    temp.put(Freq.DAILY, RecurrenceConfigurationRepository.TYPE_DAY);
    temp.put(Freq.WEEKLY, RecurrenceConfigurationRepository.TYPE_WEEK);
    temp.put(Freq.MONTHLY, RecurrenceConfigurationRepository.TYPE_MONTH);
    temp.put(Freq.YEARLY, RecurrenceConfigurationRepository.TYPE_YEAR);

    RECURRENCE_TYPES_FREQ_MAP = HashBiMap.create(temp);
  }

  @Override
  public RecurrenceConfiguration generateFromRRule(String rule, LocalDate startDate) {
    RecurrenceConfiguration recConfig = new RecurrenceConfiguration();
    try {
      RecurrenceRule rRule = new RecurrenceRule(rule);

      if (RECURRENCE_TYPES_FREQ_MAP.get(rRule.getFreq()) == null) {
        throw new InvalidRecurrenceRuleException(I18n.get("Invalide recurrence(frequency) type"));
      }
      recConfig.setRecurrenceType(RECURRENCE_TYPES_FREQ_MAP.get(rRule.getFreq()));

      recConfig.setStartDate(startDate);
      recConfig.setPeriodicity(rRule.getInterval());

      if (recConfig.getRecurrenceType() == RecurrenceConfigurationRepository.TYPE_MONTH) {
        if (StringUtils.containsIgnoreCase(rule, "BYMONTHDAY")) {
          recConfig.setMonthRepeatType(RecurrenceConfigurationRepository.REPEAT_TYPE_MONTH);
          if (rRule.hasPart(Part.BYMONTHDAY)) {
            // TODO to set field DAY of month
          }
        } else if (StringUtils.containsIgnoreCase(rule, "BYDAY")) {
          recConfig.setMonthRepeatType(RecurrenceConfigurationRepository.REPEAT_TYPE_WEEK);
        }
      }

      if (rRule.isInfinite()) {
        recConfig.setEndType(RecurrenceConfigurationRepository.END_TYPE_NEVER);
      } else if (rRule.getUntil() != null) {
        recConfig.setEndType(RecurrenceConfigurationRepository.END_TYPE_DATE);
        DateTime dateTime = rRule.getUntil();
        recConfig.setEndDate(
            LocalDate.of(dateTime.getYear(), dateTime.getMonth() + 1, dateTime.getDayOfMonth()));
      } else {
        recConfig.setEndType(RecurrenceConfigurationRepository.END_TYPE_REPET);
        recConfig.setRepetitionsNumber(rRule.getCount());
      }

      setWeekDays(rRule, recConfig);
      recConfig.setRecurrenceRule(rule);

    } catch (InvalidRecurrenceRuleException e) {
      return null;
    }

    recConfig.setRecurrenceName(computeRecurrenceName(recConfig));

    return recConfig;
  }

  protected RecurrenceConfiguration setWeekDays(
      RecurrenceRule rRule, RecurrenceConfiguration recConfig) {
    if (!rRule.hasPart(Part.BYDAY)) {
      return recConfig;
    }
    for (WeekdayNum day : rRule.getByDayPart()) {
      switch (day.weekday) {
        case SU:
          recConfig.setSunday(true);
          break;
        case MO:
          recConfig.setMonday(true);
          break;
        case TU:
          recConfig.setTuesday(true);
          break;
        case WE:
          recConfig.setWednesday(true);
          break;
        case TH:
          recConfig.setThursday(true);
          break;
        case FR:
          recConfig.setFriday(true);
          break;
        case SA:
          recConfig.setSaturday(true);
          break;
        default:
          break;
      }
    }
    return recConfig;
  }

  @Override
  public String computeRecurrenceRule(RecurrenceConfiguration recurConf) {

    Freq freq = RECURRENCE_TYPES_FREQ_MAP.inverse().get(recurConf.getRecurrenceType());

    RecurrenceRule rRule = new RecurrenceRule(freq);

    rRule.setInterval(recurConf.getPeriodicity());
    LocalDate startDate = recurConf.getStartDate();

    try {
      if (recurConf.getRecurrenceType() == RecurrenceConfigurationRepository.TYPE_WEEK) {
        rRule.setByDayPart(getDayList(recurConf));
      } else if (recurConf.getRecurrenceType() == RecurrenceConfigurationRepository.TYPE_MONTH) {
        if (recurConf.getMonthRepeatType() == RecurrenceConfigurationRepository.REPEAT_TYPE_WEEK) {
          WeekFields weekFields = WeekFields.SUNDAY_START;
          int weekNumber = startDate.get(weekFields.weekOfMonth());
          String dayOfWeek = startDate.getDayOfWeek().name().substring(0, 2).toUpperCase();
          WeekdayNum weekDayNum = new WeekdayNum(weekNumber, Weekday.valueOf(dayOfWeek));
          rRule.setByDayPart(Arrays.asList(weekDayNum));
          rRule.setWeekStart(Weekday.SU);
        } else {
          rRule.setByPart(Part.BYMONTHDAY, startDate.getDayOfMonth());
        }
      } else if (recurConf.getRecurrenceType() == RecurrenceConfigurationRepository.TYPE_YEAR) {
        rRule.setByPart(Part.BYYEARDAY, startDate.getDayOfYear());
      }

      switch (recurConf.getEndType()) {
        case RecurrenceConfigurationRepository.END_TYPE_NEVER:
          break;
        case RecurrenceConfigurationRepository.END_TYPE_DATE:
          rRule.setUntil(
              new DateTime(
                  TimeZone.getTimeZone(ZoneId.systemDefault()),
                  LocalDateTime.of(recurConf.getEndDate(), LocalTime.MAX)
                      .atZone(ZoneId.systemDefault())
                      .toInstant()
                      .toEpochMilli()));
          break;
        case RecurrenceConfigurationRepository.END_TYPE_REPET:
          rRule.setCount(recurConf.getRepetitionsNumber());
        default:
          break;
      }

    } catch (InvalidRecurrenceRuleException e) {
      TraceBackService.trace(e);
    }

    String ruleStr = rRule.toString();

    return ruleStr;
  }

  protected List<WeekdayNum> getDayList(RecurrenceConfiguration recurConf) {
    List<WeekdayNum> dayList = new ArrayList<>();

    if (recurConf.getSunday()) {
      WeekdayNum weekDayNum = new WeekdayNum(0, Weekday.SU);
      dayList.add(weekDayNum);
    }
    if (recurConf.getMonday()) {
      WeekdayNum weekDayNum = new WeekdayNum(0, Weekday.MO);
      dayList.add(weekDayNum);
    }
    if (recurConf.getTuesday()) {
      WeekdayNum weekDayNum = new WeekdayNum(0, Weekday.TU);
      dayList.add(weekDayNum);
    }
    if (recurConf.getWednesday()) {
      WeekdayNum weekDayNum = new WeekdayNum(0, Weekday.WE);
      dayList.add(weekDayNum);
    }
    if (recurConf.getThursday()) {
      WeekdayNum weekDayNum = new WeekdayNum(0, Weekday.TH);
      dayList.add(weekDayNum);
    }
    if (recurConf.getFriday()) {
      WeekdayNum weekDayNum = new WeekdayNum(0, Weekday.FR);
      dayList.add(weekDayNum);
    }
    if (recurConf.getSaturday()) {
      WeekdayNum weekDayNum = new WeekdayNum(0, Weekday.SA);
      dayList.add(weekDayNum);
    }

    return dayList;
  }

  @Override
  // TODO to check for case like every month on 4th Tuesday
  public String computeRecurrenceName(RecurrenceConfiguration recurrConf) {
    String recurrName = "";
    switch (recurrConf.getRecurrenceType()) {
      case RecurrenceConfigurationRepository.TYPE_DAY:
        if (recurrConf.getPeriodicity() == 1) {
          recurrName += I18n.get("Every day");
        } else {
          recurrName += String.format(I18n.get("Every %d days"), recurrConf.getPeriodicity());
        }

        if (recurrConf.getEndType() == RecurrenceConfigurationRepository.END_TYPE_REPET) {
          recurrName +=
              String.format(", " + I18n.get("%d times"), recurrConf.getRepetitionsNumber());
        } else if (recurrConf.getEndDate() != null) {
          recurrName +=
              ", " + I18n.get("until the") + " " + recurrConf.getEndDate().format(DATE_FORMAT);
        }
        break;

      case RecurrenceConfigurationRepository.TYPE_WEEK:
        if (recurrConf.getPeriodicity() == 1) {
          recurrName += I18n.get("Every week") + " ";
        } else {
          recurrName +=
              String.format(I18n.get("Every %d weeks") + " ", recurrConf.getPeriodicity());
        }
        if (recurrConf.getMonday()
            && recurrConf.getTuesday()
            && recurrConf.getWednesday()
            && recurrConf.getThursday()
            && recurrConf.getFriday()
            && !recurrConf.getSaturday()
            && !recurrConf.getSunday()) {
          recurrName += I18n.get("every week's day");
        } else if (recurrConf.getMonday()
            && recurrConf.getTuesday()
            && recurrConf.getWednesday()
            && recurrConf.getThursday()
            && recurrConf.getFriday()
            && recurrConf.getSaturday()
            && recurrConf.getSunday()) {
          recurrName += I18n.get("everyday");
        } else {
          recurrName += I18n.get("on") + " ";
          if (recurrConf.getMonday()) {
            recurrName += I18n.get("mon,");
          }
          if (recurrConf.getTuesday()) {
            recurrName += I18n.get("tues,");
          }
          if (recurrConf.getWednesday()) {
            recurrName += I18n.get("wed,");
          }
          if (recurrConf.getThursday()) {
            recurrName += I18n.get("thur,");
          }
          if (recurrConf.getFriday()) {
            recurrName += I18n.get("fri,");
          }
          if (recurrConf.getSaturday()) {
            recurrName += I18n.get("sat,");
          }
          if (recurrConf.getSunday()) {
            recurrName += I18n.get("sun,");
          }
        }

        if (recurrConf.getEndType() == RecurrenceConfigurationRepository.END_TYPE_REPET) {
          recurrName +=
              String.format(" " + I18n.get("%d times"), recurrConf.getRepetitionsNumber());
        } else if (recurrConf.getEndDate() != null) {
          recurrName +=
              " " + I18n.get("until the") + " " + recurrConf.getEndDate().format(DATE_FORMAT);
        }
        break;

      case RecurrenceConfigurationRepository.TYPE_MONTH:
        if (recurrConf.getPeriodicity() == 1) {
          recurrName +=
              I18n.get("Every month the") + " " + recurrConf.getStartDate().getDayOfMonth();
        } else {
          recurrName +=
              String.format(
                  I18n.get("Every %d months the %d"),
                  recurrConf.getPeriodicity(),
                  recurrConf.getStartDate().getDayOfMonth());
        }

        if (recurrConf.getEndType() == RecurrenceConfigurationRepository.END_TYPE_REPET) {
          recurrName +=
              String.format(", " + I18n.get("%d times"), recurrConf.getRepetitionsNumber());
        } else if (recurrConf.getEndDate() != null) {
          recurrName +=
              ", " + I18n.get("until the") + " " + recurrConf.getEndDate().format(DATE_FORMAT);
        }
        break;

      case RecurrenceConfigurationRepository.TYPE_YEAR:
        if (recurrConf.getPeriodicity() == 1) {
          recurrName += I18n.get("Every year the") + recurrConf.getStartDate().format(MONTH_FORMAT);
        } else {
          recurrName +=
              String.format(
                  I18n.get("Every %d years the %s"),
                  recurrConf.getPeriodicity(),
                  recurrConf.getStartDate().format(MONTH_FORMAT));
        }

        if (recurrConf.getEndType() == RecurrenceConfigurationRepository.END_TYPE_REPET) {
          recurrName +=
              String.format(", " + I18n.get("%d times"), recurrConf.getRepetitionsNumber());
        } else if (recurrConf.getEndDate() != null) {
          recurrName +=
              ", " + I18n.get("until the") + " " + recurrConf.getEndDate().format(DATE_FORMAT);
        }
        break;

      default:
        break;
    }
    return recurrName;
  }
}
