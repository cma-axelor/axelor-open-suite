/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.businessproject.web;

import com.axelor.apps.businessproject.exception.IExceptionMessage;
import com.axelor.apps.businessproject.service.PurchaseOrderLineProjectService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderLineRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PurchaseOrderLineProjectController {

  @Inject private PurchaseOrderLineProjectService purchaseOrderLineProjectService;

  @Inject private ProjectRepository projectRepository;

  @Inject private PurchaseOrderLineRepository purchaseOrderLineRepo;

  /**
   * Set project from context selected lines
   *
   * @param request
   * @param response
   */
  public void setProject(ActionRequest request, ActionResponse response) {

    try {

      Project project = request.getContext().asType(Project.class);
      project = projectRepository.find(project.getId());

      setProject(request, response, project);

    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  private void setProject(ActionRequest request, ActionResponse response, Project project) {

    List<Map<String, Object>> purchaseOrderLineSet =
        (List<Map<String, Object>>) request.getContext().get("purchaseOrderLineSet");

    if (purchaseOrderLineSet == null || purchaseOrderLineSet.isEmpty()) {
      response.setFlash(IExceptionMessage.LINES_NOT_SELECTED);
    } else {
      List<Long> lineIds =
          purchaseOrderLineSet
              .stream()
              .map(it -> Long.parseLong(it.get("id").toString()))
              .collect(Collectors.toList());
      purchaseOrderLineProjectService.setProject(lineIds, project);
      response.setAttr("$purchaseOrderLineSet", "hidden", true);
      response.setAttr("addSelectedPOLinesBtn", "hidden", true);
      response.setAttr("unlinkSelectedPOLinesBtn", "hidden", true);
      response.setAttr("cancelManagePOLinesBtn", "hidden", true);
      response.setAttr("purchaseOrderLinePanel", "refresh", true);
      response.setAttr("purchaseOrderPanel", "refresh", true);
      response.setAttr("selectNewPOLinesBtn", "readonly", false);
      response.setAttr("managePOLinesBtn", "readonly", false);
    }
  }

  /**
   * Remove project from selected lines
   *
   * @param request
   * @param response
   */
  public void unsetProject(ActionRequest request, ActionResponse response) {

    try {
      setProject(request, response, null);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  /**
   * Invert value of 'toInvoice' field and save the record
   *
   * @param request
   * @param response
   */
  @Transactional
  public void updateToInvoice(ActionRequest request, ActionResponse response) {
    try {
      PurchaseOrderLine purchaseOrderLine = request.getContext().asType(PurchaseOrderLine.class);
      purchaseOrderLine = purchaseOrderLineRepo.find(purchaseOrderLine.getId());
      purchaseOrderLine.setToInvoice(!purchaseOrderLine.getToInvoice());
      purchaseOrderLineRepo.save(purchaseOrderLine);
      response.setValue("toInvoice", purchaseOrderLine.getToInvoice());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
