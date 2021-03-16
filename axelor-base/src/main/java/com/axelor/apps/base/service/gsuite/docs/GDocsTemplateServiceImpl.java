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

import com.axelor.apps.base.db.GDocsConfig;
import com.axelor.apps.base.db.GDocsConfigLine;
import com.axelor.apps.base.db.GDocsTemplate;
import com.axelor.apps.base.service.gsuite.GSuiteService;
import com.axelor.common.ObjectUtils;
import com.axelor.db.JpaRepository;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.schema.views.Selection.Option;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.ReplaceAllTextRequest;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.docs.v1.model.SubstringMatchCriteria;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.inject.Inject;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class GDocsTemplateServiceImpl implements GDocsTemplateService {
  
  protected static final String TEMPLATE_TEXT_FORMAT = "{{%s}}";

  protected GSuiteService gSuiteService;
  protected GDriveService gDriveService;
  protected GDocsService gDocsService;

  @Inject
  public GDocsTemplateServiceImpl(
      GSuiteService gSuiteService, GDriveService gDriveService, GDocsService gDocsService) {
    this.gSuiteService = gSuiteService;
    this.gDriveService = gDriveService;
    this.gDocsService = gDocsService;
  }

  @Override
  public void generateAll(GDocsConfig config)
      throws AxelorException, ClassNotFoundException, IOException {
    for (GDocsConfigLine line : config.getConfigLineList()) {
      for (Model model :
          JpaRepository.of(getModelClass(line.getMetaModel())).all().order("id").fetch(3, 0)) {
        generate(config, line, model);
      }
    }
  }

  @Override 
  public void generate(GDocsConfig config, GDocsConfigLine configLine, Model model)
      throws AxelorException, ClassNotFoundException {

    final Docs docsService = gSuiteService.getDocsService(config.getId());
    final Drive driveService = gSuiteService.getDrive(config.getId());
    String destinationFolder = configLine.getDestinationFolderID();

    Class<? extends Model> modelClass = getModelClass(configLine.getMetaModel());

    List<Request> requests = generateRequests(modelClass, model);

    for (GDocsTemplate template : configLine.getTemplateList()) {

      try {
        final String fileName = computeOutputFileName(model, modelClass);
        final String templateGDocId = template.getTargetId();
        File documentCopyFile =
            gDriveService.copyFile(destinationFolder, fileName, templateGDocId, driveService);

        String documentCopyId = documentCopyFile.getId();

        gDocsService.batchUpdate(documentCopyId, requests, docsService);

      } catch (IOException e) {
        TraceBackService.trace(e);
      }
    }
  }

  protected String computeOutputFileName(Model model, Class<? extends Model> modelClass) {
    return modelClass.getSimpleName() + "#" + model.getId();
  }

  protected List<Request> generateRequests(Class<?> klass, Model bean) {
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

    if (property.isImage() || property.isVersion() || property.isCollection()) {
      return requests;
    }

    Object value = mapper.get(bean, name);

    if (property.isReference() && value != null) {
      requests.addAll(handleReferenceField(value, property, name));
    }

    value = getSimpleFieldValue(property, value);

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

      if (field.isCollection() || field.isImage() || field.isVersion()) {
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

      value = getSimpleFieldValue(property, value);

      String computedName = name + "." + field.getName();
      requests.add(createRequest(computedName, value));
    }

    return requests;
  }

  protected Object getSimpleFieldValue(Property property, Object value) {
    if (ObjectUtils.notEmpty(property.getSelection()) && value != null) {
      value = getSelectionTitle(property, value);
    }

    if (value instanceof BigDecimal) {
      value = ((BigDecimal) value).setScale(2, RoundingMode.HALF_EVEN);
    }
    return value;
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

  @SuppressWarnings("unchecked")
  protected Class<? extends Model> getModelClass(MetaModel metaModel)
      throws ClassNotFoundException {
    Class<? extends Model> klass = (Class<? extends Model>) Class.forName(metaModel.getFullName());
    return klass;
  }
}
