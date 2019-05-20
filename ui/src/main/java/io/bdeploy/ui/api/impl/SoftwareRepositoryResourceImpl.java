package io.bdeploy.ui.api.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.ui.api.SoftwareRepositoryResource;
import io.bdeploy.ui.api.SoftwareResource;

public class SoftwareRepositoryResourceImpl implements SoftwareRepositoryResource {

    @Context
    private ResourceContext rc;

    @Inject
    private ActivityReporter reporter;

    @Inject
    private BHiveRegistry registry;

    @Override
    public List<SoftwareRepositoryConfiguration> list() {
        List<SoftwareRepositoryConfiguration> result = new ArrayList<>();
        for (Map.Entry<String, BHive> entry : registry.getAll().entrySet()) {
            SoftwareRepositoryConfiguration cfg = new SoftwareRepositoryManifest(entry.getValue()).read();
            if (cfg != null) {
                result.add(cfg);
            }
        }
        return result;
    }

    @Override
    public SoftwareResource getSoftwareResource(String softwareRepository) {
        return rc.initResource(new SoftwareResourceImpl(getSoftwareRepositoryHive(softwareRepository)));
    }

    private BHive getSoftwareRepositoryHive(String softwareRepository) {
        BHive hive = registry.get(softwareRepository);
        if (hive == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return hive;
    }

    @Override
    public void create(SoftwareRepositoryConfiguration config) {
        // TODO: better storage location selection mechanism in the future.
        Path storage = registry.getLocations().iterator().next();
        Path hive = storage.resolve(config.name);

        if (Files.isDirectory(hive)) {
            throw new WebApplicationException("Hive path already exists: ", Status.NOT_ACCEPTABLE);
        }

        BHive h = new BHive(hive.toUri(), reporter);
        new SoftwareRepositoryManifest(h).update(config);
        registry.register(config.name, h);
    }

    private BHive getRepoHive(String repo) {
        BHive hive = registry.get(repo);
        if (hive == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return hive;
    }

    @Override
    public SoftwareRepositoryConfiguration read(String repo) {
        return new SoftwareRepositoryManifest(getRepoHive(repo)).read();
    }

    @Override
    public void update(String repo, SoftwareRepositoryConfiguration config) {
        RuntimeAssert.assertEquals(repo, config.name, "Repository update changes repository name");
        new SoftwareRepositoryManifest(getRepoHive(repo)).update(config);
    }

    @Override
    public void delete(String repo) {
        BHive bHive = registry.get(repo);
        if (bHive == null) {
            throw new WebApplicationException("Repository '" + repo + "' does not exist");
        }
        registry.unregister(repo);
        PathHelper.deleteRecursive(Paths.get(bHive.getUri()));
    }

}
