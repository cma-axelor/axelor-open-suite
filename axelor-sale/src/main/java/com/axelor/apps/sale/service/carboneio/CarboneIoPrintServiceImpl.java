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
package com.axelor.apps.sale.service.carboneio;

import com.axelor.app.AppSettings;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.saleorder.SaleOrderService;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.axelor.studio.db.AppSale;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class CarboneIoPrintServiceImpl implements CarboneIoPrintService {

  protected static final String REQUEST_KEY_DATA = "data";
  protected static final String REQUEST_KEY_TEMPLATE = "template";
  protected static final String CARBONEIO_SERVER_URL = "carboneio.server.url";

  protected SaleOrderService saleOrderService;
  protected ObjectMapper mapper;
  protected AppSaleService appSaleService;

  @Inject
  public CarboneIoPrintServiceImpl(
      SaleOrderService saleOrderService, ObjectMapper mapper, AppSaleService appSaleService) {
    this.saleOrderService = saleOrderService;
    this.mapper = mapper;
    this.appSaleService = appSaleService;
  }

  @Override
  public Path print(List<Model> models) throws AxelorException {

    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {

      CarboneInput input = prepareData(models);

      HttpResponse httpresponse = executeRequest(httpclient, input);

      if (httpresponse.getStatusLine() != null
          && httpresponse.getStatusLine().getStatusCode() != 200) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get("Http code ") + httpresponse.getStatusLine().getStatusCode());
      }

      HttpEntity entity = httpresponse.getEntity();
      return createExportFile(input.getReportName(), input.getConvertTo(), entity);
    } catch (IOException e) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY, I18n.get("Error in downloading file"));
    }
  }

  protected HttpResponse executeRequest(CloseableHttpClient httpclient, CarboneInput input)
      throws IOException {
    HttpPost httpPost = new HttpPost(AppSettings.get().get(CARBONEIO_SERVER_URL).trim());

    httpPost.setEntity(getHttpPostEntity(input));
    return httpclient.execute(httpPost);
  }

  protected HttpEntity getHttpPostEntity(CarboneInput input) throws JsonProcessingException {
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

    MetaFile carboneSaleTemplate = appSaleService.getAppSale().getCarboneSaleTemplate();
    File tempFile = MetaFiles.getPath(carboneSaleTemplate).toFile();

    builder.addBinaryBody(
        REQUEST_KEY_TEMPLATE, tempFile, ContentType.DEFAULT_BINARY, tempFile.getName());

    String writeValueAsString = mapper.writeValueAsString(input);
    builder.addTextBody(REQUEST_KEY_DATA, writeValueAsString, ContentType.MULTIPART_FORM_DATA);

    return builder.build();
  }

  protected Path createExportFile(String fileName, String extension, HttpEntity entity)
      throws IOException {
    File resultFile = MetaFiles.createTempFile(fileName, "." + extension).toFile();
    FileUtils.copyInputStreamToFile(entity.getContent(), resultFile);
    return resultFile.toPath();
  }

  @Override
  public CarboneInput prepareData(List<Model> models) {
    CarboneInput input = new CarboneInput();
    input.setReportName(models.get(0).getClass().getSimpleName());
    String carboneOutputFormat =
        Optional.ofNullable(appSaleService.getAppSale())
            .map(AppSale::getCarboneOutputFormat)
            .filter(StringUtils::notBlank)
            .orElse(ReportSettings.FORMAT_PDF);
    input.setConvertTo(carboneOutputFormat);
    input.setLang("en");
    input.setData(getSaleOrderData(models));
    return input;
  }

  protected List<Map<String, Object>> getSaleOrderData(List<Model> models) {
    return models.stream().map(this::getDataMap).collect(Collectors.toList());
  }

  protected Map<String, Object> getDataMap(Model model) {
    return Mapper.toMap(JPA.find(model.getClass(), model.getId()));
  }
}
