package io.bdeploy.ui.api;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.jersey.ActivityScope;

@Path("/softwarerepository")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SoftwareRepositoryResource {

    @GET
    public List<SoftwareRepositoryConfiguration> list();

    @Path("/{softwareRepository}/content")
    public SoftwareResource getSoftwareResource(@ActivityScope @PathParam("softwareRepository") String softwareRepository);

}
