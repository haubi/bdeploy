package io.bdeploy.ui.api;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.glassfish.jersey.media.multipart.FormDataParam;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.NoScopeInheritance;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.configuration.instance.ApplicationValidationDto;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributesRecord;
import io.bdeploy.interfaces.manifest.banner.InstanceBannerRecord;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.manifest.statistics.ClientUsageData;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.jersey.ActivityScope;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.ui.dto.HistoryFilterDto;
import io.bdeploy.ui.dto.HistoryResultDto;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceManifestHistoryDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.InstanceOverallStatusDto;
import io.bdeploy.ui.dto.InstanceVersionDto;
import io.bdeploy.ui.dto.StringEntryChunkDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface InstanceResource {

    public static final String PATH_DOWNLOAD_APP_ICON = "/{instance}/{applicationId}/icon";
    public static final String PATH_DOWNLOAD_APP_SPLASH = "/{instance}/{applicationId}/splash";

    @GET
    public List<InstanceDto> list();

    /**
     * Synchronizes all instances and receives their overall status, including potential hints on to what is "wrong".
     */
    @POST
    @Path("/syncAll")
    public List<InstanceOverallStatusDto> syncInstances(List<Manifest.Key> instances);

    @GET
    @Path("/{instance}/versions")
    public List<InstanceVersionDto> listVersions(@ActivityScope @PathParam("instance") String instanceId);

    @PUT
    @RequiredPermission(permission = Permission.ADMIN)
    public void create(InstanceConfiguration config, @QueryParam("managedServer") String managedServer);

    @GET
    @Path("/{instance}")
    public InstanceConfiguration read(@ActivityScope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/{versionTag}")
    public InstanceConfiguration readVersion(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("versionTag") String versionTag);

    @POST
    @Path("/{instance}/update")
    @RequiredPermission(permission = Permission.WRITE)
    public void update(@ActivityScope @PathParam("instance") String instanceId, InstanceUpdateDto config,
            @QueryParam("managedServer") String managedServer, @QueryParam("expect") String expectedTag);

    @DELETE
    @Path("/{instance}/delete")
    @RequiredPermission(permission = Permission.ADMIN)
    public void delete(@ActivityScope @PathParam("instance") String instanceId);

    @DELETE
    @Path("/{instance}/deleteVersion/{tag}")
    @NoScopeInheritance // don't inherit the instance group scope, global admin is required.
    @RequiredPermission(permission = Permission.ADMIN)
    public void deleteVersion(@ActivityScope @PathParam("instance") String instanceId, @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/{tag}/nodeConfiguration")
    public InstanceNodeConfigurationListDto getNodeConfigurations(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("tag") String versionTag);

    @GET
    @Path("/{instance}/{tag}/minionConfiguration")
    @RequiredPermission(permission = Permission.READ)
    public Map<String, MinionDto> getMinionConfiguration(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("tag") String versionTag);

    @GET
    @Path("/{instance}/{tag}/minionState")
    @RequiredPermission(permission = Permission.READ)
    public Map<String, MinionStatusDto> getMinionState(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("tag") String versionTag);

    @GET
    @Path("/{instance}/{tag}/install")
    @RequiredPermission(permission = Permission.WRITE)
    public void install(@ActivityScope @PathParam("instance") String instanceId, @ActivityScope @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/{tag}/uninstall")
    @RequiredPermission(permission = Permission.WRITE)
    public void uninstall(@ActivityScope @PathParam("instance") String instanceId, @ActivityScope @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/{tag}/activate")
    @RequiredPermission(permission = Permission.WRITE)
    public void activate(@ActivityScope @PathParam("instance") String instanceId, @ActivityScope @PathParam("tag") String tag);

    @POST
    @Path("/{instance}/updateProductVersion/{target}")
    @RequiredPermission(permission = Permission.WRITE)
    public InstanceUpdateDto updateProductVersion(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("target") String productTag, InstanceUpdateDto state);

    @POST
    @Path("/{instance}/validate")
    @RequiredPermission(permission = Permission.WRITE)
    public List<ApplicationValidationDto> validate(@ActivityScope @PathParam("instance") String instanceId,
            InstanceUpdateDto state);

    @GET
    @Path("/{instance}/{tag}/history")
    @RequiredPermission(permission = Permission.READ)
    public InstanceManifestHistoryDto getHistory(@ActivityScope @PathParam("instance") String instanceId,
            @ActivityScope @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/state")
    @RequiredPermission(permission = Permission.READ)
    public InstanceStateRecord getDeploymentStates(@ActivityScope @PathParam("instance") String instanceId);

    @GET
    @Path("/purposes")
    public List<InstancePurpose> getPurposes();

    @Path("/{instance}/processes")
    @RequiredPermission(permission = Permission.READ)
    public ProcessResource getProcessResource(@ActivityScope @PathParam("instance") String instanceId);

    @Path("/{instance}/cfgFiles")
    @RequiredPermission(permission = Permission.READ)
    public ConfigFileResource getConfigResource(@ActivityScope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/{applicationId}/clickAndStart")
    public ClickAndStartDescriptor getClickAndStartDescriptor(@PathParam("instance") String instanceId,
            @PathParam("applicationId") String applicationId);

    @GET
    @Path("/{instance}/{applicationId}/installer/zip")
    @Produces(MediaType.TEXT_PLAIN)
    public String createClientInstaller(@PathParam("instance") String instanceId,
            @PathParam("applicationId") String applicationId);

    @GET
    @Unsecured
    @Path(PATH_DOWNLOAD_APP_ICON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadIcon(@PathParam("instance") String instanceId, @PathParam("applicationId") String applicationId);

    @GET
    @Unsecured
    @Path(PATH_DOWNLOAD_APP_SPLASH)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadSplash(@PathParam("instance") String instanceId, @PathParam("applicationId") String applicationId);

    @GET
    @Unsecured
    @Path("/{instance}/export/{tag}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @RequiredPermission(permission = Permission.WRITE)
    public Response exportInstance(@ActivityScope @PathParam("instance") String instanceId, @PathParam("tag") String tag);

    @POST
    @Path("/{instance}/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequiredPermission(permission = Permission.WRITE)
    public List<Key> importInstance(@FormDataParam("file") InputStream inputStream,
            @ActivityScope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/output/{tag}/{app}")
    @RequiredPermission(permission = Permission.READ)
    public RemoteDirectory getOutputEntry(@ActivityScope @PathParam("instance") String instanceId,
            @ActivityScope @PathParam("tag") String tag, @PathParam("app") String app);

    @POST
    @Path("/{instance}/content/{minion}")
    @RequiredPermission(permission = Permission.READ)
    public StringEntryChunkDto getContentChunk(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("minion") String minion, RemoteDirectoryEntry entry, @QueryParam("offset") long offset,
            @QueryParam("limit") long limit);

    @POST
    @Path("/{instance}/request/{minion}")
    @RequiredPermission(permission = Permission.READ)
    public String getContentStreamRequest(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("minion") String minion, RemoteDirectoryEntry entry);

    @POST
    @Path("/{instance}/requestMultiZip/{minion}")
    @RequiredPermission(permission = Permission.READ)
    public String getContentMultiZipStreamRequest(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("minion") String minion, List<RemoteDirectoryEntry> entry);

    @POST
    @Path("/{instance}/data/update/{minion}")
    @RequiredPermission(permission = Permission.WRITE)
    public void updateDataFiles(@ActivityScope @PathParam("instance") String instanceId, @PathParam("minion") String minion,
            List<FileStatusDto> updates);

    @POST
    @Path("/{instance}/delete/{minion}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void deleteDataFile(@ActivityScope @PathParam("instance") String instanceId, @PathParam("minion") String minion,
            RemoteDirectoryEntry entry);

    @GET
    @Unsecured
    @Path("/{instance}/stream/{token}")
    @RequiredPermission(permission = Permission.READ)
    public Response getContentStream(@ActivityScope @PathParam("instance") String instanceId, @PathParam("token") String token);

    @GET
    @Unsecured
    @Path("/{instance}/streamMultiZip/{token}")
    @RequiredPermission(permission = Permission.READ)
    public Response getContentMultiZipStream(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("token") String token);

    @POST
    @Path("/{instance}/check-ports/{minion}")
    @RequiredPermission(permission = Permission.READ)
    public Map<Integer, Boolean> getPortStates(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("minion") String minion, List<Integer> ports);

    @GET
    @Path("/{instance}/banner")
    public InstanceBannerRecord getBanner(@ActivityScope @PathParam("instance") String instanceId);

    @POST
    @Path("/{instance}/banner")
    @RequiredPermission(permission = Permission.WRITE)
    public void updateBanner(@ActivityScope @PathParam("instance") String instanceId, InstanceBannerRecord instanceBannerRecord);

    @POST
    @Path("/{instance}/history")
    @RequiredPermission(permission = Permission.READ)
    public HistoryResultDto getInstanceHistory(@ActivityScope @PathParam("instance") String instanceId, HistoryFilterDto filter);

    @GET
    @Path("/{instance}/attributes")
    @RequiredPermission(permission = Permission.READ)
    public CustomAttributesRecord getAttributes(@ActivityScope @PathParam("instance") String instanceId);

    @POST
    @Path("/{instance}/attributes")
    @RequiredPermission(permission = Permission.WRITE)
    public void updateAttributes(@ActivityScope @PathParam("instance") String instanceId, CustomAttributesRecord attributes);

    @GET
    @Path("/{instance}/clientUsage")
    @RequiredPermission(permission = Permission.CLIENT)
    public ClientUsageData getClientUsageData(@ActivityScope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/uiDirect/{app}/{ep : [^/]+}")
    @RequiredPermission(permission = Permission.CLIENT)
    public String getUiDirectUrl(@ActivityScope @PathParam("instance") String instance, @PathParam("app") String application,
            @PathParam("ep") String endpoint);

}
