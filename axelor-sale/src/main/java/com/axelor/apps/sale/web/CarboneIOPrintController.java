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
import com.axelor.apps.sale.service.carboneio.CarboneIoPrintService;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.axelor.utils.file.PdfTool;
import com.google.inject.Singleton;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class CarboneIOPrintController {

  public void printSaleOrder(ActionRequest request, ActionResponse response) {
    try {
      Context context = request.getContext();

      List<Model> models = new ArrayList<>();
      if (context.get("_ids") != null) {
        List<Integer> ids = (List<Integer>) context.get("_ids");
        ids.stream()
            .map(String::valueOf)
            .map(Long::valueOf)
            .map(id -> JPA.find(SaleOrder.class, id))
            .forEach(models::add);

      } else if (request.getContext().get("id") != null) {
        String idStr = request.getContext().get("id").toString();
        models.add(
            JPA.find(
                (Class<? extends Model>) request.getContext().getContextClass(),
                Long.valueOf(idStr)));
      } else {
        response.setError(I18n.get("No records selected to print"));
        return;
      }
      Path exportFilePath = Beans.get(CarboneIoPrintService.class).print(models);
      final File file = exportFilePath.toFile();

      String fileLink = PdfTool.getFileLinkFromPdfFile(file, file.getName());
      response.setView(ActionView.define(I18n.get("SaleOrder")).add("html", fileLink).map());

    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
