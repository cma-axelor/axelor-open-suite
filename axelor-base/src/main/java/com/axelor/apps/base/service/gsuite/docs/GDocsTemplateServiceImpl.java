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
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.schema.views.Selection.Option;
import com.google.api.services.docs.v1.model.ReplaceAllTextRequest;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.docs.v1.model.SubstringMatchCriteria;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class GDocsTemplateServiceImpl implements GDocsTemplateService {

  private static final String TEMPLATE_TEXT_FORMAT = "{{%s}}";

  @Override
  public List<Request> generateRequests(Class<?> klass, Model bean) {
    List<Request> requests = new ArrayList<>();
    Mapper mapper = Mapper.of(klass);
    for (Property property : mapper.getProperties()) {
      requests.addAll(generate(bean, mapper, property));
    }
    return requests;
  }

  protected List<Request> generate(Model bean, Mapper mapper, Property property) {
    List<Request> requests = new ArrayList<>();
    String name = property.getName();

    if (property.isCollection() || property.isImage() || property.isVersion()) {
      return requests;
    }

    Object value = mapper.get(bean, name);

    if (property.isReference() && value != null) {
      requests.addAll(handleReferenceField(value, property, name));
    }

    if (ObjectUtils.notEmpty(property.getSelection()) && value != null) {
      value = getSelectionTitle(property, value);
    }

    if (value instanceof BigDecimal) {
      value = ((BigDecimal) value).setScale(2, RoundingMode.HALF_EVEN);
    }

    requests.add(createRequest(name, value));

    return requests;
  }

  protected Object getSelectionTitle(Property property, Object value) {
    Option selectionItem =
        MetaStore.getSelectionItem(property.getSelection(), String.valueOf(value));
    value = selectionItem == null ? null : I18n.get(selectionItem.getTitle());
    return value;
  }

  protected List<Request> handleReferenceField(Object bean, Property property, String name) {
    List<Request> requests = new ArrayList<>();
    final Class<?> targetClass = property.getTarget();

    if (MetaFile.class.equals(targetClass)) {
      // TODO to handle case of Images
      return requests;
    }

    Mapper mapper = Mapper.of(targetClass);

    for (Property field : mapper.getProperties()) {

      if (field.isCollection()) {
        continue;
      }

      Object value = mapper.get(bean, field.getName());
      if (field.isReference() && value != null) {
        String computedName = name + "." + field.getName();
        Mapper m = Mapper.of(field.getTarget());
        String targetName = field.getTargetName();
        if (targetName == null) {
          targetName = "id";
        }
        requests.add(createRequest(computedName, m.get(value, targetName)));
      }

      if (ObjectUtils.notEmpty(property.getSelection()) && value != null) {
        value = getSelectionTitle(property, value);
      }

      if (value instanceof BigDecimal) {
        value = ((BigDecimal) value).setScale(2, RoundingMode.HALF_EVEN);
      }

      String computedName = name + "." + field.getName();
      requests.add(createRequest(computedName, value));
    }

    return requests;
  }

  protected Request createRequest(String key, Object value) {
    final String replaceText = ObjectUtils.isEmpty(value) ? "" : value.toString();
    final SubstringMatchCriteria matchingCriteria =
        new SubstringMatchCriteria()
            .setText(String.format(TEMPLATE_TEXT_FORMAT, key))
            .setMatchCase(true);

    final ReplaceAllTextRequest replaceTextReq =
        new ReplaceAllTextRequest().setContainsText(matchingCriteria).setReplaceText(replaceText);

    return new Request().setReplaceAllText(replaceTextReq);
  }
}
