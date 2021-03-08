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
package com.axelor.apps.base.service.gsuite;

import com.axelor.app.AppSettings;
import com.axelor.app.AvailableAppSettings;
import com.axelor.apps.base.db.AppBase;
import com.axelor.apps.base.db.GDocsConfig;
import com.axelor.apps.base.db.repo.GDocsConfigRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.DocsScopes;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Arrays;

@Singleton
public class GSuiteService {

  private static final String WS_GSUITE_SYNC_URL = "/ws/gsuite";

  protected static final String[] SCOPES = new String[] {DocsScopes.DOCUMENTS, DriveScopes.DRIVE};

  protected GoogleAuthorizationCodeFlow flow = null;

  protected static final String APP_NAME =
      AppSettings.get().get("application.name", "Axelor open suite");

  protected static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  protected AppBaseService appBaseService;

  protected GDocsConfigRepository docsConfigRepo;

  @Inject
  public GSuiteService(AppBaseService appBaseService, GDocsConfigRepository docsConfigRepo) {
    this.appBaseService = appBaseService;
    this.docsConfigRepo = docsConfigRepo;
  }

  public Drive getDrive(Long configId) throws AxelorException {
    return new Drive.Builder(getHttpTransport(), JSON_FACTORY, getCredential(configId))
        .setApplicationName(APP_NAME)
        .build();
  }

  public Docs getDocsService(Long configId) throws AxelorException {
    return new Docs.Builder(getHttpTransport(), JSON_FACTORY, getCredential(configId))
        .setApplicationName(APP_NAME)
        .build();
  }

  public String getAuthenticationUrl(Long configId) throws AxelorException {

    return getFlow()
        .newAuthorizationUrl()
        .setRedirectUri(getRedirectURL(null))
        .setApprovalPrompt("force")
        .setState(configId.toString())
        .setAccessType("offline")
        .build();
  }

  @Transactional(rollbackOn = {AxelorException.class})
  public void setGoogleCredential(Long configId, String code) throws AxelorException {

    try {
      GoogleAuthorizationCodeFlow flow = getFlow();

      GoogleAuthorizationCodeTokenRequest newTokenRequest = flow.newTokenRequest(code);
      TokenResponse response = newTokenRequest.setRedirectUri(getRedirectURL(null)).execute();
      flow.createAndStoreCredential(response, configId.toString());

      GDocsConfig account = docsConfigRepo.find(configId);
      account.setIsAuthorized(true);

      docsConfigRepo.save(account);

    } catch (IOException e) {
      throw new AxelorException(e.getCause(), TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }
  }

  protected GoogleAuthorizationCodeFlow getFlow() throws AxelorException {
    if (flow == null) {

      try {

        final AppBase app = appBaseService.getAppBase();
        final String clientId = app.getClientId();
        final String clientSecret = app.getClientSecret();

        if (StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              IExceptionMessage.NO_CONFIGURATION_EXCEPTION);
        }

        flow = generateFlow(clientId, clientSecret, getHttpTransport());
      } catch (IOException e) {
        throw new AxelorException(e.getCause(), TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
      }
    }
    return flow;
  }

  protected Credential getCredential(Long configId) throws AxelorException {

    GoogleAuthorizationCodeFlow flow = getFlow();

    Credential credential;
    try {
      credential = flow.loadCredential(configId.toString());
    } catch (IOException e) {
      throw new AxelorException(e.getCause(), TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }

    return credential;
  }

  protected GoogleAuthorizationCodeFlow generateFlow(
      final String clientId, final String clientSecret, final HttpTransport httpTransport)
      throws IOException {
    return new GoogleAuthorizationCodeFlow.Builder(
            httpTransport, JSON_FACTORY, clientId, clientSecret, Arrays.asList(SCOPES))
        .setDataStoreFactory(getFileDataStoreFactory())
        .build();
  }

  protected HttpTransport getHttpTransport() throws AxelorException {
    HttpTransport httpTransport;
    try {
      httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      throw new AxelorException(e.getCause(), TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }
    return httpTransport;
  }

  protected FileDataStoreFactory getFileDataStoreFactory() throws IOException {
    final File fileUploadDir = Paths.get(AvailableAppSettings.FILE_UPLOAD_DIR, "gsuite").toFile();
    return new FileDataStoreFactory(fileUploadDir);
  }

  protected String getRedirectURL(String urlPart) {
    return AppSettings.get().getBaseURL()
        + (StringUtils.isBlank(urlPart) ? WS_GSUITE_SYNC_URL : urlPart);
  }
}
