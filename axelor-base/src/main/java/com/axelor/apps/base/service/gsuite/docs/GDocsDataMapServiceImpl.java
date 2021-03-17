/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.base.service.gsuite.docs;

import com.axelor.common.ObjectUtils;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaStore;
import com.axelor.meta.schema.views.Selection.Option;
import com.google.common.base.Preconditions;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GDocsDataMapServiceImpl implements GDocsDataMapService {

  protected static final int MAX_LEVEL = 3;

  @Override
  public Map<String, Object> generateDataMap(Class<?> klass, Model bean) {
    Preconditions.checkNotNull(bean);

    Map<String, Object> context = new HashMap<>();
    Mapper mapper = Mapper.of(klass);

    for (Property property : mapper.getProperties()) {
      Object value = mapper.get(bean, property.getName());

      if (ObjectUtils.isEmpty(value)) {
        context.put(property.getName(), format(property, value));
      }

      if (property.isReference()) {
        context.putAll(getReferenceFieldDataMap(property.getName(), property, value, 0));
        continue;
      }

      if (property.isCollection()) {
        Mapper targetMapper = Mapper.of(property.getTarget());
        for (Property field : targetMapper.getProperties()) {
          String valueStr = null;
          if (field.isCollection()) {
            continue;
          }

          if (ObjectUtils.notEmpty(value)) {
            List<String> values = new ArrayList<>();
            for (Object item : (List<?>) value) {
              item =
                  JPA.find((Class<? extends Model>) property.getTarget(), ((Model) item).getId());
              final Object v = targetMapper.get(item, field.getName());
              if (field.isReference() && v != null) {
                Mapper m = Mapper.of(field.getTarget());
                String targetField = field.getTargetName();
                targetField = targetField == null ? "id" : targetField;
                values.add(format(field, m.get(v, targetField)));
              } else {
                values.add(format(field, v));
              }
            }
            valueStr = values.stream().collect(Collectors.joining(",", "[", "]"));
          }
          String computedName = String.format("%s.%s", property.getName(), field.getName());
          context.put(computedName, format(property, valueStr));
        }
        continue;
      }

      context.put(property.getName(), format(property, value));
    }
    return context;
  }

  protected Map<String, Object> getReferenceFieldDataMap(
      String prefix, Property property, Object bean, Integer level) {

    final Map<String, Object> map = new HashMap<>();
    Mapper targetMapper = Mapper.of(property.getTarget());

    for (Property field : targetMapper.getProperties()) {

      final String name = field.getName();
      final String computedName = String.format("%s.%s", prefix, name);

      Object value = ObjectUtils.isEmpty(bean) ? null : targetMapper.get(bean, name);

      if (ObjectUtils.isEmpty(value)) {
        map.put(computedName, format(property, value));
      }

      if (field.isReference() && level <= MAX_LEVEL) {
        map.putAll(getReferenceFieldDataMap(computedName, field, value, ++level));
        continue;
      }

      if (property.isCollection()) {
        continue;
      }
      map.put(computedName, format(field, value));
    }

    return map;
  }

  protected String format(Property property, Object value) {

    if (value == null) {
      return "";
    }

    if (ObjectUtils.notEmpty(property.getSelection())) {
      final String selectionTitle = getSelectionTitle(property, value);
      return selectionTitle == null ? value.toString() : selectionTitle;
    }

    if (value == Boolean.TRUE) {
      return I18n.get("True");
    }

    if (value == Boolean.FALSE) {
      return I18n.get("False");
    }

    if (value instanceof BigDecimal) {
      return ((BigDecimal) value).setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    if (value instanceof LocalDate) {
      return ((LocalDate) value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    if (value instanceof LocalDateTime) {
      return ((LocalDateTime) value).format(DateTimeFormatter.ISO_DATE_TIME);
    }

    return value.toString();
  }

  protected String getSelectionTitle(Property property, Object value) {
    Option selectionItem =
        MetaStore.getSelectionItem(property.getSelection(), String.valueOf(value));
    return selectionItem == null ? null : I18n.get(selectionItem.getTitle());
  }
}
