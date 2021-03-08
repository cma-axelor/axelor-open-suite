package com.axelor.apps.base.web;

import com.axelor.apps.base.db.GDocsConfig;
import com.axelor.apps.base.db.GDocsConfigLine;
import com.axelor.apps.base.db.GDocsTemplate;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.GDocsConfigRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.gsuite.GSuiteService;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest;
import com.google.api.services.docs.v1.model.ReplaceAllTextRequest;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.docs.v1.model.SubstringMatchCriteria;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GDocsTemplateController {

  public void generate(ActionRequest request, ActionResponse response) {
    GDocsConfig config = request.getContext().asType(GDocsConfig.class);
    config = Beans.get(GDocsConfigRepository.class).find(config.getId());
    Product bean = Beans.get(ProductRepository.class).find(1l);

    List<Request> requests = new ArrayList<>();
    Mapper mapper = Mapper.of(Product.class);
    for (Property field : mapper.getProperties()) {
      String name = field.getName();
      if (field.isCollection()) {
        continue;
      }

      Object value = mapper.get(bean, name);

      if (field.isReference() && value != null) {

        Mapper refFieldMapper = Mapper.of(field.getTarget());

        if (MetaFile.class.equals(field.getTarget())) {

          // TODO to handle case of Images

        } else {

          for (Property field2 : refFieldMapper.getProperties()) {
            if (field2.isCollection() || field2.isReference()) {
              continue;
            }
            String computedName = name + "." + field2.getName();
            Object targetValue = refFieldMapper.get(value, field2.getName());

            requests.add(
                new Request()
                    .setReplaceAllText(
                        new ReplaceAllTextRequest()
                            .setContainsText(
                                new SubstringMatchCriteria()
                                    .setText(String.format("{{%s}}", computedName))
                                    .setMatchCase(true))
                            .setReplaceText(targetValue == null ? "" : targetValue.toString())));
          }
        }
      }

      requests.add(
          new Request()
              .setReplaceAllText(
                  new ReplaceAllTextRequest()
                      .setContainsText(
                          new SubstringMatchCriteria()
                              .setText(String.format("{{%s}}", name))
                              .setMatchCase(true))
                      .setReplaceText(value == null ? "" : value.toString())));
    }

    try {
      final GSuiteService gSuiteService = Beans.get(GSuiteService.class);
      Drive drive = gSuiteService.getDrive(config.getId());
      Docs docs = gSuiteService.getDocsService(config.getId());
      for (GDocsConfigLine line : config.getConfigLineList()) {
        String destinationFolder = line.getDestinationFolderID();
        for (GDocsTemplate template : line.getTemplateList()) {

          File copyMetadata = new File().setName("Copy Title");
          copyMetadata.setParents(Collections.singletonList(destinationFolder));
          File documentCopyFile =
              drive.files().copy(template.getTargetId(), copyMetadata).execute();

          String documentCopyId = documentCopyFile.getId();

          BatchUpdateDocumentRequest body = new BatchUpdateDocumentRequest();
          docs.documents().batchUpdate(documentCopyId, body.setRequests(requests)).execute();
        }
      }
    } catch (AxelorException | IOException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
