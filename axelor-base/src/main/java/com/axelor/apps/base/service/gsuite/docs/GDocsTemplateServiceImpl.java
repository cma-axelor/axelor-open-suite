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
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.db.MetaModel;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentResponse;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.docs.v1.model.ParagraphElement;
import com.google.api.services.docs.v1.model.ReplaceAllTextRequest;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.docs.v1.model.Response;
import com.google.api.services.docs.v1.model.StructuralElement;
import com.google.api.services.docs.v1.model.SubstringMatchCriteria;
import com.google.api.services.docs.v1.model.TableCell;
import com.google.api.services.docs.v1.model.TableRow;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.inject.Inject;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GDocsTemplateServiceImpl implements GDocsTemplateService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  protected static final String TEMPLATE_TEXT_FORMAT = "{{%s}}";

  protected GSuiteService gSuiteService;
  protected GDriveService gDriveService;
  protected GDocsService gDocsService;
  protected GDocsDataMapService gDocsDataMapService;

  @Inject
  public GDocsTemplateServiceImpl(
      GSuiteService gSuiteService,
      GDriveService gDriveService,
      GDocsService gDocsService,
      GDocsDataMapService gDocsDataMapService) {
    this.gSuiteService = gSuiteService;
    this.gDriveService = gDriveService;
    this.gDocsService = gDocsService;
    this.gDocsDataMapService = gDocsDataMapService;
  }

  @Override
  public void generateAll(GDocsConfig config)
      throws AxelorException, ClassNotFoundException, IOException {
    for (GDocsConfigLine line : config.getConfigLineList()) {
      final List<? extends Model> data =
          JpaRepository.of(getModelClass(line.getMetaModel())).all().order("id").fetch(10, 0);
      for (Model model : data) {
        generate(line, config.getId(), model);
      }
    }
  }

  @Override
  public void generate(GDocsConfigLine configLine, Long configId, Model model)
      throws AxelorException, ClassNotFoundException {

    final Docs docsService = gSuiteService.getDocsService(configId);
    final Drive driveService = gSuiteService.getDrive(configId);

    final Class<? extends Model> modelClass = getModelClass(configLine.getMetaModel());
    final String destinationFolderID = configLine.getDestinationFolderID();
    final String fileName = computeOutputFileName(model, modelClass);

    final List<Request> requests = createRequests(modelClass, model);
    for (GDocsTemplate template : configLine.getTemplateList()) {
      try {
        File documentCopyFile =
            gDriveService.copyFile(
                destinationFolderID, fileName, template.getTargetId(), driveService);
        
        Document doc = docsService.documents().get(documentCopyFile.getId()).execute();
        
        System.err.println(readStructuralElements(doc.getBody().getContent()));;
        BatchUpdateDocumentResponse response =
            gDocsService.batchUpdate(documentCopyFile.getId(), requests, docsService);

        int numReplacements = 0;
        for (Response resp : response.getReplies()) {
          Integer occurrencesChanged = resp.getReplaceAllText().getOccurrencesChanged();
          numReplacements += occurrencesChanged == null ? 0 : occurrencesChanged;
        }
        LOG.debug(
            "Create doc with id {} for template {}",
            documentCopyFile.getId(),
            template.getTargetId());
        LOG.debug("Replaced {} text instances", numReplacements);

      } catch (IOException e) {
        TraceBackService.trace(e);
      }
    }
  }
  
  private static String readStructuralElements(List<StructuralElement> elements) {
	    StringBuilder sb = new StringBuilder();
	    for (StructuralElement element : elements) {
	      if (element.getParagraph() != null) {

	      } else if (element.getTable() != null) {
	        // The text in table cells are in nested Structural Elements and tables may be
	        // nested.
	        for (TableRow row : element.getTable().getTableRows()) {
	          for (TableCell cell : row.getTableCells()) {
	            sb.append(readStructuralElements(cell.getContent()));
	          }
	        }
	      } else if (element.getTableOfContents() != null) {
	        // The text in the TOC is also in a Structural Element.
	        sb.append(readStructuralElements(element.getTableOfContents().getContent()));
	      }
	    }
	    return sb.toString();
	  }
  

  protected List<Request> createRequests(Class<?> klass, Model bean) {
    Function<Map.Entry<String, Object>, Request> mapper =
        (entry) -> createRequest(entry.getKey(), entry.getValue());

    Map<String, Object> context = gDocsDataMapService.generateDataMap(klass, bean);
    return context.entrySet().stream().map(mapper).collect(Collectors.toList());
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
    return (Class<? extends Model>) Class.forName(metaModel.getFullName());
  }

  protected String computeOutputFileName(Model model, Class<? extends Model> modelClass) {
    return modelClass.getSimpleName() + "#" + model.getId();
  }
}
