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
package com.axelor.apps.base.web;

import com.axelor.apps.base.db.GDocsConfig;
import com.axelor.apps.base.service.gsuite.GSuiteService;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class GDocsConfigController {

  public void setAuthUrl(ActionRequest request, ActionResponse response)
      throws AxelorException, IOException {
    GDocsConfig config = request.getContext().asType(GDocsConfig.class);
    String authUrl = Beans.get(GSuiteService.class).getAuthenticationUrl(config.getId());
    final String authUrlTitle =
        "<a href='%s'>Google authentication url (click to authenticate account)</a>";
    response.setAttr("authUrl", "title", String.format(I18n.get(authUrlTitle), authUrl));
  }
}
