/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2026 Axelor (<http://axelor.com>).
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
package com.axelor.apps.bankpayment.web;

import com.axelor.apps.bankpayment.service.bankreconciliation.BankReconciliationStressDataGeneratorService;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Singleton;
import java.util.Map;

@Singleton
public class BankReconciliationStressDataGeneratorController {

  public void generate(ActionRequest request, ActionResponse response) {
    try {
      Context context = request.getContext();

      String message =
          Beans.get(BankReconciliationStressDataGeneratorService.class)
              .generate(
                  getId(context.get("company")),
                  getId(context.get("bankDetails")),
                  getId(context.get("journal")),
                  getId(context.get("currency")),
                  getId(context.get("cashAccount")),
                  getId(context.get("counterpartAccount")),
                  getInteger(context.get("scenarioModeSelect")),
                  getInteger(context.get("generationProfileSelect")),
                  getInteger(context.get("queryCount")),
                  getInteger(context.get("reconciliationLineCount")),
                  getInteger(context.get("workloadCount")),
                  getString(context.get("prefix")));

      response.setInfo(message);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  protected Long getId(Object value) {
    if (!(value instanceof Map)) {
      return null;
    }

    Object id = ((Map<?, ?>) value).get("id");
    if (id instanceof Number) {
      return ((Number) id).longValue();
    }

    return id != null ? Long.valueOf(id.toString()) : null;
  }

  protected Integer getInteger(Object value) {
    if (value == null) {
      return null;
    }

    if (value instanceof Number) {
      return ((Number) value).intValue();
    }

    return Integer.valueOf(value.toString());
  }

  protected String getString(Object value) {
    return value != null ? value.toString() : null;
  }
}
