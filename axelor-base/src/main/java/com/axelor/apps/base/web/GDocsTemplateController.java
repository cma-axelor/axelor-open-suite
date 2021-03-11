package com.axelor.apps.base.web;

import com.axelor.apps.base.db.GDocsConfig;
import com.axelor.apps.base.db.GDocsConfigLine;
import com.axelor.apps.base.db.GDocsTemplate;
import com.axelor.apps.base.db.repo.GDocsConfigRepository;
import com.axelor.apps.base.service.gsuite.GSuiteService;
import com.axelor.apps.base.service.gsuite.docs.GDocsTemplateService;
import com.axelor.db.JpaRepository;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.loader.ModuleManager;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class GDocsTemplateController {

  public void generate(ActionRequest request, ActionResponse response) {
    GDocsConfig config = request.getContext().asType(GDocsConfig.class);
    config = Beans.get(GDocsConfigRepository.class).find(config.getId());

    try {
      final GSuiteService gSuiteService = Beans.get(GSuiteService.class);
      Drive drive = gSuiteService.getDrive(config.getId());
      Docs docs = gSuiteService.getDocsService(config.getId());
      for (GDocsConfigLine line : config.getConfigLineList()) {
        String destinationFolder = line.getDestinationFolderID();
        Class<? extends Model> klass =
            (Class<? extends Model>) Class.forName(line.getMetaModel().getFullName());
        for (Model model : JpaRepository.of(klass).all().fetch(3, 0)) {

          List<Request> requests =
              Beans.get(GDocsTemplateService.class).generateRequests(klass, model);
          for (GDocsTemplate template : line.getTemplateList()) {

            File copyMetadata = new File().setName(klass.getSimpleName() + "#" + model.getId());
            copyMetadata.setParents(Collections.singletonList(destinationFolder));
            File documentCopyFile =
                drive.files().copy(template.getTargetId(), copyMetadata).execute();

            String documentCopyId = documentCopyFile.getId();

            BatchUpdateDocumentRequest body = new BatchUpdateDocumentRequest();
            docs.documents().batchUpdate(documentCopyId, body.setRequests(requests)).execute();
          }
        }
      }
    } catch (AxelorException | IOException | ClassNotFoundException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
