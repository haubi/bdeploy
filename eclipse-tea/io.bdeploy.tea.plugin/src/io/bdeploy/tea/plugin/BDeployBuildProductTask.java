/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.tea.core.services.TaskingLog;
import org.eclipse.tea.library.build.config.BuildDirectories;
import org.eclipse.tea.library.build.services.TeaBuildVersionService;
import org.eclipse.tea.library.build.util.FileUtils;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.interfaces.descriptor.product.ProductDescriptor;
import io.bdeploy.interfaces.descriptor.product.ProductVersionDescriptor;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.dependencies.DependencyFetcher;
import io.bdeploy.interfaces.manifest.dependencies.LocalDependencyFetcher;
import io.bdeploy.interfaces.manifest.dependencies.RemoteDependencyFetcher;
import io.bdeploy.tea.plugin.services.BDeployApplicationBuild;

@SuppressWarnings("restriction")
public class BDeployBuildProductTask {

    private final ProductDesc desc;
    private Manifest.Key key;
    private File target;

    public BDeployBuildProductTask(ProductDesc desc) {
        this.desc = desc;
    }

    @Override
    public String toString() {
        return "BDeploy Product Build: " + desc.productInfo.getFileName();
    }

    @Execute
    public void build(BuildDirectories dirs, TaskingLog log, TeaBuildVersionService bvs, BDeployConfig cfg) throws Exception {
        File prodInfoDir = new File(dirs.getProductDirectory(), "prod-info");
        target = new File(dirs.getProductDirectory(), "bhive");

        if (cfg.clearBHive) {
            log.info("Clearing " + target);
            FileUtils.deleteDirectory(target);
        } else {
            log.info("Using existing " + target);
        }

        String fullVersion = calculateVersion(bvs);

        ActivityReporter.Stream reporter = new ActivityReporter.Stream(log.info());
        try (BHive bhive = new BHive(target.toURI(), reporter)) {
            // 1: generate product version file.
            ProductVersionDescriptor pvd = new ProductVersionDescriptor();
            pvd.version = fullVersion;

            for (BDeployApplicationBuild ad : desc.apps) {
                Map<OperatingSystem, String> em = pvd.appInfo.computeIfAbsent(ad.name,
                        (n) -> new EnumMap<>(OperatingSystem.class));
                em.put(ad.os, ad.source.get().getAbsolutePath());
            }

            pvd.labels.put("X-Built-by", System.getProperty("user.name"));
            pvd.labels.put("X-Built-on", InetAddress.getLocalHost().getHostName());

            // try to find project for product info
            Repository repo = findRepoForProduct(desc.productInfo);
            if (repo != null) {
                pvd.labels.put("X-GIT-LocalBranch", repo.getFullBranch());
                pvd.labels.put("X-GIT-CommitId", repo.getRefDatabase().exactRef(repo.getFullBranch()).getObjectId().name());
            }

            FileUtils.deleteDirectory(prodInfoDir);
            FileUtils.mkdirs(prodInfoDir);

            try (OutputStream os = new FileOutputStream(new File(prodInfoDir, "product-versions.yaml"))) {
                os.write(StorageHelper.toRawYamlBytes(pvd));
            }

            File prodInfoYaml = new File(prodInfoDir, "product-info.yaml");
            try (InputStream is = Files.newInputStream(desc.productInfo); OutputStream os = new FileOutputStream(prodInfoYaml)) {
                ProductDescriptor pd = StorageHelper.fromYamlStream(is, ProductDescriptor.class);
                pd.versionFile = "product-versions.yaml";
                os.write(StorageHelper.toRawYamlBytes(pd));

                if (pd.configTemplates != null && !pd.configTemplates.isEmpty()) {
                    File source = desc.productInfo.getParent().resolve(pd.configTemplates).toFile();
                    if (!source.isDirectory()) {
                        throw new IllegalStateException("Cannot find " + source);
                    }
                    File cfgDir = new File(prodInfoDir, source.getName());
                    FileUtils.deleteDirectory(cfgDir);
                    FileUtils.mkdirs(cfgDir);
                    FileUtils.copyDirectory(source, cfgDir);
                }
            }

            // 2: create product and import into bhive
            RemoteService svc = cfg.bdeployServer == null ? null
                    : new RemoteService(UriBuilder.fromUri(cfg.bdeployServer).build(), cfg.bdeployServerToken);

            DependencyFetcher fetcher;
            if (svc != null) {
                fetcher = new RemoteDependencyFetcher(svc, cfg.bdeployTargetInstanceGroup, reporter);
            } else {
                fetcher = new LocalDependencyFetcher();
            }

            log.info("Importing product from " + prodInfoYaml);
            key = ProductManifest.importFromDescriptor(prodInfoYaml.getAbsolutePath(), bhive, fetcher);
        }

    }

    public Manifest.Key getKey() {
        return key;
    }

    public File getTarget() {
        return target;
    }

    private Repository findRepoForProduct(Path productInfo) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IContainer[] containers = root.findContainersForLocationURI(productInfo.toAbsolutePath().toUri());

        if (containers == null || containers.length == 0) {
            return null;
        }

        IProject prj = containers[0].getProject();
        if (prj == null) {
            return null;
        }

        RepositoryMapping mapping = RepositoryMapping.getMapping(prj);
        if (mapping == null) {
            return null;
        }

        return mapping.getRepository();
    }

    private String calculateVersion(TeaBuildVersionService bvs) {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmm");

        String q = bvs.getQualifierFormat();
        if (!q.contains("%D")) {
            q += "%D";
        }

        return bvs.getBuildVersion().replace("qualifier", q.replace("%D", format.format(date)));
    }

    static class ProductDesc {

        Path productInfo;
        List<BDeployApplicationBuild> apps = new ArrayList<>();
    }

}
