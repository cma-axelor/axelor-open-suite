package com.axelor.web;

import com.axelor.apps.base.service.gsuite.GSuiteService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;
import java.io.IOException;
import java.net.URI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("/gsuite")
@RequestScoped
public class GSuiteWebService {

  @Inject private GSuiteService gSuiteService;

  @GET
  public Response setCode(
      @QueryParam("state") String accountId,
      @QueryParam("code") String code,
      @Context UriInfo uriInfo)
      throws IOException, AxelorException {

    if (code != null) {
      gSuiteService.setGoogleCredential(Long.parseLong(accountId), code.trim());
    }

    String baseUri = uriInfo.getBaseUri().toString();
    String formUri = baseUri.replace("/ws/", "/#");

    return Response.seeOther(URI.create(formUri)).build();
  }
}
