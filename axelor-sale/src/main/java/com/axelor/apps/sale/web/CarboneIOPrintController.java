/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2023 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.apps.sale.web;

import com.axelor.apps.base.ResponseMessageType;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.service.carboneio.CarboneIoPrintService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.utils.file.PdfTool;
import com.google.inject.Singleton;
import java.io.File;
import java.nio.file.Path;

@Singleton
public class CarboneIOPrintController {

  public void printSaleOrder(ActionRequest request, ActionResponse response) {
    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
    try {
      saleOrder = Beans.get(SaleOrderRepository.class).find(saleOrder.getId());
      Path exportFilePath = Beans.get(CarboneIoPrintService.class).print(saleOrder);
      final File file = exportFilePath.toFile();

      String fileLink = PdfTool.getFileLinkFromPdfFile(file, file.getName());
      response.setView(ActionView.define(I18n.get("SaleOrder")).add("html", fileLink).map());

    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
