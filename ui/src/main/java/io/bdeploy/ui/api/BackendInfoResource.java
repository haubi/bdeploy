package io.bdeploy.ui.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.ui.dto.AttachIdentDto;
import io.bdeploy.ui.dto.BackendInfoDto;

@Path("/backend-info")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface BackendInfoResource {

    @GET
    @Unsecured
    @Path("/version")
    public BackendInfoDto getVersion();

    /**
     * @return a DTO which can be used to attach this server to another server.
     */
    @GET
    @Path("/attach-ident")
    public AttachIdentDto getAttachIdentification();

}
