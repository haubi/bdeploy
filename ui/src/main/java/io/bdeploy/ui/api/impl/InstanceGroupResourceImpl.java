package io.bdeploy.ui.api.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import com.google.common.io.ByteStreams;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.ProductResource;

public class InstanceGroupResourceImpl implements InstanceGroupResource {

    @Inject
    private BHiveRegistry registry;

    @Inject
    private ActivityReporter reporter;

    @Context
    private ResourceContext rc;

    @Inject
    private AuthService auth;

    @Context
    private SecurityContext context;

    @Override
    public List<InstanceGroupConfiguration> list() {
        List<InstanceGroupConfiguration> result = new ArrayList<>();
        for (BHive hive : registry.getAll().values()) {
            InstanceGroupConfiguration cfg = new InstanceGroupManifest(hive).read();
            if (cfg != null) {
                result.add(cfg);
            }
        }
        return result;
    }

    @Override
    public void create(InstanceGroupConfiguration config) {
        // TODO: better storage location selection mechanism in the future.
        Path storage = registry.getLocations().iterator().next();
        Path hive = storage.resolve(config.name);

        if (Files.isDirectory(hive)) {
            throw new WebApplicationException("Hive path already exists: ", Status.NOT_ACCEPTABLE);
        }

        BHive h = new BHive(hive.toUri(), reporter);
        new InstanceGroupManifest(h).update(config);
        registry.register(config.name, h);

        auth.addRecentlyUsedInstanceGroup(context.getUserPrincipal().getName(), config.name);
    }

    private BHive getGroupHive(String group) {
        BHive hive = registry.get(group);
        if (hive == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return hive;
    }

    @Override
    public InstanceGroupConfiguration read(String group) {
        return new InstanceGroupManifest(getGroupHive(group)).read();
    }

    @Override
    public void update(String group, InstanceGroupConfiguration config) {
        auth.addRecentlyUsedInstanceGroup(context.getUserPrincipal().getName(), group);
        RuntimeAssert.assertEquals(group, config.name, "Group update changes group name");
        new InstanceGroupManifest(getGroupHive(group)).update(config);
    }

    @Override
    public void delete(String group) {
        BHive bHive = registry.get(group);
        if (bHive == null) {
            throw new WebApplicationException("Hive '" + group + "' does not exist");
        }
        registry.unregister(group);
        PathHelper.deleteRecursive(Paths.get(bHive.getUri()));
    }

    @Override
    public void updateImage(String group, InputStream imageData) {
        InstanceGroupConfiguration config = read(group);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ByteStreams.copy(imageData, baos);
            ObjectId id = getGroupHive(group).execute(new ImportObjectOperation().setData(baos.toByteArray()));
            config.logo = id;
            update(group, config);
        } catch (IOException e) {
            throw new WebApplicationException("Cannot read image", e);
        }
    }

    @Override
    public InputStream readImage(String group) {
        ObjectId id = read(group).logo;
        if (id == null) {
            return null;
        }
        return getGroupHive(group).execute(new ObjectLoadOperation().setObject(id));
    }

    @Override
    public String createUuid(String group) {
        return UuidHelper.randomId();
    }

    @Override
    public List<URI> listMasterUrls(String group) {
        BHive hive = getGroupHive(group);
        SortedSet<Key> scan = InstanceManifest.scan(hive, true);

        return scan.stream().filter(Objects::nonNull).map(k -> InstanceManifest.of(hive, k).getConfiguration())
                .filter(x -> x.target != null).map(x -> x.target.getUri()).distinct().collect(Collectors.toList());
    }

    @Override
    public InstanceResource getInstanceResource(String group) {
        return rc.initResource(new InstanceResourceImpl(group, getGroupHive(group)));
    }

    @Override
    public ProductResource getProductResource(String group) {
        return rc.initResource(new ProductResourceImpl(getGroupHive(group)));
    }

}
