package io.bdeploy.minion.remote.jersey;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.apache.commons.codec.binary.Base64;
import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.LocalDependencyFetcher;
import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ExportTreeOperation;
import io.bdeploy.bhive.op.ImportTreeOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.common.util.TaskExecutor;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.common.util.ZipHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributesRecord;
import io.bdeploy.interfaces.manifest.banner.InstanceBannerRecord;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;
import io.bdeploy.interfaces.manifest.history.runtime.MasterRuntimeHistoryDto;
import io.bdeploy.interfaces.manifest.state.InstanceOverallStateRecord;
import io.bdeploy.interfaces.manifest.state.InstanceOverallStateRecord.OverallStatus;
import io.bdeploy.interfaces.manifest.state.InstanceState;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.manifest.statistics.ClientUsage;
import io.bdeploy.interfaces.manifest.statistics.ClientUsageData;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.CommonDirectoryEntryResource;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.NodeDeploymentResource;
import io.bdeploy.interfaces.remote.NodeProcessResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyPathWriter.DeleteAfterWrite;
import io.bdeploy.jersey.JerseyWriteLockService.LockingResource;
import io.bdeploy.jersey.JerseyWriteLockService.WriteLock;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.ui.api.NodeManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;

@LockingResource
public class MasterNamedResourceImpl implements MasterNamedResource {

    private static final Logger log = LoggerFactory.getLogger(MasterNamedResourceImpl.class);

    private final BHive hive;
    private final ActivityReporter reporter;
    private final MinionRoot root;

    @Context
    private SecurityContext context;

    @Inject
    private NodeManager nodes;

    public MasterNamedResourceImpl(MinionRoot root, BHive hive, ActivityReporter reporter) {
        this.root = root;
        this.hive = hive;
        this.reporter = reporter;
    }

    private InstanceState getState(InstanceManifest im, BHive hive) {
        return im.getState(hive);
    }

    @Override
    public InstanceStateRecord getInstanceState(String instance) {
        InstanceManifest im = InstanceManifest.load(hive, instance, null);
        return getState(im, hive).read();
    }

    @Override
    public void install(Key key) {
        InstanceManifest imf = InstanceManifest.of(hive, key);
        SortedMap<String, Key> fragmentReferences = imf.getInstanceNodeManifests();
        fragmentReferences.remove(InstanceManifest.CLIENT_NODE_NAME);

        // assure that the product has been pushed to the master (e.g. by central).
        Boolean productExists = hive.execute(new ManifestExistsOperation().setManifest(imf.getConfiguration().product));
        if (!Boolean.TRUE.equals(productExists)) {
            throw new WebApplicationException("Cannot find required product " + imf.getConfiguration().product, Status.NOT_FOUND);
        }

        TaskExecutor executor = new TaskExecutor(reporter);
        for (Map.Entry<String, Manifest.Key> entry : fragmentReferences.entrySet()) {
            String nodeName = entry.getKey();
            Manifest.Key toDeploy = entry.getValue();
            MinionDto node = nodes.getNodeConfigIfOnline(nodeName);

            assertNotNull(node, "Node not available: " + nodeName);
            assertNotNull(toDeploy, "Cannot lookup node manifest on master: " + toDeploy);

            // grab all required manifests from the applications
            InstanceNodeManifest inm = InstanceNodeManifest.of(hive, toDeploy);
            LocalDependencyFetcher localDeps = new LocalDependencyFetcher();
            PushOperation pushOp = new PushOperation().setRemote(node.remote);
            for (ApplicationConfiguration app : inm.getConfiguration().applications) {
                pushOp.addManifest(app.application);
                ApplicationManifest amf = ApplicationManifest.of(hive, app.application);

                // applications /must/ follow the ScopedManifestKey rules.
                ScopedManifestKey smk = ScopedManifestKey.parse(app.application);

                // the dependency must be here. it has been pushed here with the product,
                // since the product /must/ reference all direct dependencies.
                localDeps.fetch(hive, amf.getDescriptor().runtimeDependencies, smk.getOperatingSystem())
                        .forEach(pushOp::addManifest);
            }

            // Make sure the node has the manifest
            pushOp.addManifest(toDeploy);

            // Create the task that pushes all manifests and then installs them on the remote
            NodeDeploymentResource deployment = ResourceProvider.getVersionedResource(node.remote, NodeDeploymentResource.class,
                    context);
            executor.add(() -> {
                hive.execute(pushOp);
                deployment.install(toDeploy);
            });
        }

        // Execute all tasks
        try {
            executor.run("Installing");
        } catch (Exception ex) {
            throw new WebApplicationException("Installation failed", ex, Status.INTERNAL_SERVER_ERROR);
        }

        getState(imf, hive).install(key.getTag());
        imf.getHistory(hive).recordAction(Action.INSTALL, context.getUserPrincipal().getName(), null);
    }

    @Override
    @WriteLock
    public void activate(Key key) {
        InstanceManifest imf = InstanceManifest.of(hive, key);

        if (!isFullyDeployed(imf)) {
            throw new WebApplicationException(
                    "Given manifest for UUID " + imf.getConfiguration().uuid + " is not fully deployed: " + key,
                    Status.NOT_FOUND);
        }

        // record de-activation
        String activeTag = imf.getState(hive).read().activeTag;
        if (activeTag != null) {
            try {
                InstanceManifest oldIm = InstanceManifest.load(hive, imf.getConfiguration().uuid, activeTag);
                oldIm.getHistory(hive).recordAction(Action.DEACTIVATE, context.getUserPrincipal().getName(), null);

                // make sure all nodes which no longer participate are deactivated.
                for (Map.Entry<String, Manifest.Key> oldNode : oldIm.getInstanceNodeManifests().entrySet()) {
                    // deactivation by activation later on.
                    if (imf.getInstanceNodeManifests().containsKey(oldNode.getKey())) {
                        continue;
                    }

                    MinionDto node = nodes.getNodeConfigIfOnline(oldNode.getKey());
                    if (node == null) {
                        log.info("Minion {} not available for de-activation", oldNode.getKey());
                        continue;
                    }

                    ResourceProvider.getVersionedResource(node.remote, NodeDeploymentResource.class, context)
                            .deactivate(oldNode.getValue());
                }
            } catch (Exception e) {
                // in case the old version disappeared (manual deletion, automatic migration,
                // ...) we do not
                // want to fail to activate the new version...
                log.debug("Cannot set old version to de-activated", e);
            }
        }

        SortedMap<String, Key> fragments = imf.getInstanceNodeManifests();
        try (Activity activating = reporter.start("Activating on minions...", fragments.size())) {
            for (Map.Entry<String, Manifest.Key> entry : fragments.entrySet()) {
                String nodeName = entry.getKey();
                if (InstanceManifest.CLIENT_NODE_NAME.equals(nodeName)) {
                    continue;
                }
                Manifest.Key toDeploy = entry.getValue();
                MinionDto node = nodes.getNodeConfigIfOnline(nodeName);

                assertNotNull(node, "Node not available: " + nodeName);
                assertNotNull(toDeploy, "Cannot lookup node manifest on master: " + toDeploy);

                NodeDeploymentResource deployment = ResourceProvider.getVersionedResource(node.remote,
                        NodeDeploymentResource.class, context);

                try {
                    deployment.activate(toDeploy);
                } catch (Exception e) {
                    // log but don't forward exception to the client
                    throw new WebApplicationException("Cannot activate on " + nodeName, e, Status.BAD_GATEWAY);
                }

                activating.worked(1);
            }
        }

        getState(imf, hive).activate(key.getTag());
        imf.getHistory(hive).recordAction(Action.ACTIVATE, context.getUserPrincipal().getName(), null);
    }

    /**
     * @param imf the {@link InstanceManifest} to check.
     * @param ignoreOffline whether to regard an instance as deployed even if a
     *            participating node is offline.
     * @return whether the given {@link InstanceManifest} is fully deployed to all
     *         required minions.
     */
    private boolean isFullyDeployed(InstanceManifest imf) {
        SortedMap<String, Key> imfs = imf.getInstanceNodeManifests();
        // No configuration -> no requirements, so always fully deployed.
        if (imfs.isEmpty()) {
            return true;
        }
        // check all minions for their respective availability.
        String instanceId = imf.getConfiguration().uuid;
        for (Map.Entry<String, Manifest.Key> entry : imfs.entrySet()) {
            String nodeName = entry.getKey();
            if (InstanceManifest.CLIENT_NODE_NAME.equals(nodeName)) {
                continue;
            }
            Manifest.Key toDeploy = entry.getValue();
            MinionDto node = nodes.getNodeConfigIfOnline(nodeName);

            assertNotNull(node, "Node not available: " + nodeName);
            assertNotNull(toDeploy, "Cannot lookup node manifest on master: " + toDeploy);

            InstanceStateRecord deployments;
            try {
                NodeDeploymentResource ndr = ResourceProvider.getVersionedResource(node.remote, NodeDeploymentResource.class,
                        context);
                deployments = ndr.getInstanceState(instanceId);
            } catch (Exception e) {
                throw new IllegalStateException("Node offline while checking state: " + nodeName, e);
            }

            if (deployments.installedTags.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("Node {} does not contain any deployment for {}", nodeName, instanceId);
                }
                return false;
            }
            if (!deployments.installedTags.contains(toDeploy.getTag())) {
                if (log.isDebugEnabled()) {
                    log.debug("Node {} does not have {} available", nodeName, toDeploy);
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public void uninstall(Key key) {
        InstanceManifest imf = InstanceManifest.of(hive, key);

        SortedMap<String, Key> fragments = imf.getInstanceNodeManifests();
        fragments.remove(InstanceManifest.CLIENT_NODE_NAME);

        TaskExecutor executor = new TaskExecutor(reporter);
        for (Map.Entry<String, Manifest.Key> entry : fragments.entrySet()) {
            String nodeName = entry.getKey();
            Manifest.Key toRemove = entry.getValue();
            MinionDto node = nodes.getNodeConfigIfOnline(nodeName);

            assertNotNull(toRemove, "Cannot lookup node manifest on master: " + toRemove);

            if (node == null) {
                // minion no longer exists or node is offline. this is recoverable as when the node is online
                // during the next cleanup cycle, it will clean itself.
                log.warn("Node not available: {}. Ignoring.", nodeName);
                continue;
            }

            NodeDeploymentResource deployment = ResourceProvider.getVersionedResource(node.remote, NodeDeploymentResource.class,
                    context);
            executor.add(() -> deployment.remove(toRemove));
        }

        // Execute all tasks
        try {
            executor.run("Uninstalling");
        } catch (Exception ex) {
            throw new WebApplicationException("Failed to uninstall.", ex, Status.INTERNAL_SERVER_ERROR);
        }

        getState(imf, hive).uninstall(key.getTag());
        imf.getHistory(hive).recordAction(Action.UNINSTALL, context.getUserPrincipal().getName(), null);
    }

    private Manifest.Key createInstanceVersion(Manifest.Key target, InstanceConfiguration config,
            SortedMap<String, InstanceNodeConfiguration> nodes) {

        InstanceManifest.Builder builder = new InstanceManifest.Builder();
        builder.setInstanceConfiguration(config);
        builder.setKey(target);

        for (Entry<String, InstanceNodeConfiguration> entry : nodes.entrySet()) {
            InstanceNodeConfiguration inc = entry.getValue();
            if (inc == null) {
                continue;
            }

            // make sure redundant data is equal to instance data.
            if (!config.name.equals(inc.name)) {
                log.warn("Instance name of node ({}) not equal to instance name ({}) - aligning.", inc.name, config.name);
                inc.name = config.name;
            }

            inc.copyRedundantFields(config);

            // make sure every application has an ID. NEW applications might have a null ID
            // to be filled out.
            for (ApplicationConfiguration cfg : inc.applications) {
                if (cfg.uid == null) {
                    cfg.uid = UuidHelper.randomId();
                    log.info("New Application {} received ID {}", cfg.name, cfg.uid);
                }
            }

            RuntimeAssert.assertEquals(inc.uuid, config.uuid, "Instance ID not set on nodes");

            InstanceNodeManifest.Builder inmb = new InstanceNodeManifest.Builder();
            inmb.addConfigTreeId(InstanceNodeManifest.ROOT_CONFIG_NAME, config.configTree);
            inmb.setMinionName(entry.getKey());
            inmb.setInstanceNodeConfiguration(inc);
            inmb.setKey(new Manifest.Key(config.uuid + '/' + entry.getKey(), target.getTag()));

            // create dedicated configuration trees for client applications where required.
            if (entry.getKey().equals(InstanceManifest.CLIENT_NODE_NAME)) {
                // client applications *may* specify config directories.
                for (var app : inc.applications) {
                    if (app.processControl == null || StringHelper.isNullOrEmpty(app.processControl.configDirs)) {
                        continue; // no dirs set.
                    }

                    // we have directories set, and need to create a dedicated config tree for the application.
                    String[] allowedPaths = app.processControl.configDirs.split(",");
                    ObjectId appTree = applyConfigUpdates(config.configTree, p -> {
                        // remove unwanted paths from p.
                        applyConfigRestrictions(allowedPaths, p, p);
                    });

                    // record the config tree for this application.
                    inmb.addConfigTreeId(app.uid, appTree);
                }
            }

            builder.addInstanceNodeManifest(entry.getKey(), inmb.insert(hive));
        }

        Manifest.Key key = builder.insert(hive);
        InstanceManifest.of(hive, key).getHistory(hive).recordAction(Action.CREATE, context.getUserPrincipal().getName(), null);
        return key;
    }

    /**
     * Client applications can specify a set of allowed paths. From the current config tree and this set of allowed
     * paths, we remove all paths which are not allowed. This builds a dedicated per-application configuration
     * which is allowed to be provided to clients - this avoids sending sensitive config files which should only
     * be available on servers.
     */
    private void applyConfigRestrictions(String[] allowedPaths, Path p, Path root) {
        try (DirectoryStream<Path> list = Files.newDirectoryStream(p)) {
            for (Path path : list) {
                if (Files.isDirectory(path)) {
                    // need to recurse to check.
                    applyConfigRestrictions(allowedPaths, path, root);
                } else {
                    // only accept if the file relative path starts with the
                    boolean ok = false;
                    for (String x : allowedPaths) {
                        Path rel = root.relativize(path);
                        if (("/" + rel.toString().replace("\\", "/")).startsWith(x.endsWith("/") ? x : (x + "/"))) {
                            ok = true;
                        }
                    }

                    if (!ok) {
                        PathHelper.deleteRecursive(path);
                    }
                }
            }
        } catch (IOException e) {
            throw new WebApplicationException("Cannot apply content restriction", e);
        }
    }

    @WriteLock
    @Override
    public Manifest.Key update(InstanceUpdateDto update, String expectedTag) {
        InstanceConfigurationDto state = update.config;
        List<FileStatusDto> configUpdates = update.files;

        InstanceConfiguration instanceConfig = state.config;
        String rootName = InstanceManifest.getRootName(instanceConfig.uuid);
        Set<Key> existing = hive.execute(new ManifestListOperation().setManifestName(rootName));
        InstanceManifest oldConfig = null;
        if (expectedTag == null && !existing.isEmpty()) {
            throw new WebApplicationException("Instance already exists: " + instanceConfig.uuid, Status.CONFLICT);
        } else if (expectedTag != null) {
            oldConfig = InstanceManifest.load(hive, instanceConfig.uuid, null);
            if (!oldConfig.getManifest().getTag().equals(expectedTag)) {
                throw new WebApplicationException("Expected version is not the current one: expected=" + expectedTag
                        + ", current=" + oldConfig.getManifest().getTag(), Status.CONFLICT);
            }
        }

        try (Transaction t = hive.getTransactions().begin()) {
            if (configUpdates != null && !configUpdates.isEmpty()) {
                // export existing tree and apply updates.
                // set/reset config tree ID on instanceConfig.
                instanceConfig.configTree = applyConfigUpdates(instanceConfig.configTree, p -> applyUpdates(configUpdates, p));
            }

            // calculate target key.
            String rootTag = hive.execute(new ManifestNextIdOperation().setManifestName(rootName)).toString();
            Manifest.Key rootKey = new Manifest.Key(rootName, rootTag);

            if ((state.nodeDtos == null || state.nodeDtos.isEmpty()) && oldConfig != null) {
                // no new node config - re-apply existing one with new tag, align redundant
                // fields.
                state.nodeDtos = readExistingNodeConfigs(oldConfig);
            }

            // does NOT validate that the product exists, as it might still reside on the
            // central server, not this one.

            SortedMap<String, InstanceNodeConfiguration> nodeMap = new TreeMap<>();
            if (state.nodeDtos != null) {
                state.nodeDtos.forEach(n -> nodeMap.put(n.nodeName, updateControlGroups(n.nodeConfiguration)));
            }

            return createInstanceVersion(rootKey, state.config, nodeMap);
        }
    }

    private InstanceNodeConfiguration updateControlGroups(InstanceNodeConfiguration nodeConfiguration) {
        if (nodeConfiguration.controlGroups.isEmpty()) {
            // nothing defined yet, fill it with the default - processes in configuration order.
            ProcessControlGroupConfiguration defGrp = new ProcessControlGroupConfiguration();
            defGrp.processOrder.addAll(nodeConfiguration.applications.stream().map(a -> a.uid).toList());

            nodeConfiguration.controlGroups.add(defGrp);
        } else {
            // make sure that all processes are in *SOME* group. if not, we try to find or create a default group.
            for (ApplicationConfiguration app : nodeConfiguration.applications) {
                Optional<ProcessControlGroupConfiguration> group = nodeConfiguration.controlGroups.stream()
                        .filter(g -> g.processOrder.contains(app.uid)).findAny();
                if (group.isEmpty()) {
                    ProcessControlGroupConfiguration defGrp = nodeConfiguration.controlGroups.stream()
                            .filter(g -> g.name.equals(ProcessControlGroupConfiguration.DEFAULT_GROUP)).findAny()
                            .orElseGet(() -> {
                                ProcessControlGroupConfiguration newDefGrp = new ProcessControlGroupConfiguration();
                                nodeConfiguration.controlGroups.add(0, newDefGrp); // default should be in front.
                                return newDefGrp;
                            });

                    // application not in any group.
                    defGrp.processOrder.add(app.uid);
                }
            }
        }

        return nodeConfiguration;
    }

    private ObjectId applyConfigUpdates(ObjectId configTree, Consumer<Path> updater) {
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory(root.getTempDir(), "cfgUp-");
            Path cfgDir = tmpDir.resolve("cfg");

            // 1. export current tree to temp directory
            exportConfigTree(configTree, cfgDir);

            // 2. apply updates to files
            updater.accept(cfgDir);

            // 3. re-import new tree from temp directory
            return hive.execute(new ImportTreeOperation().setSkipEmpty(true).setSourcePath(cfgDir));
        } catch (IOException e) {
            throw new WebApplicationException("Cannot update configuration files", e);
        } finally {
            if (tmpDir != null) {
                PathHelper.deleteRecursive(tmpDir);
            }
        }
    }

    private void exportConfigTree(ObjectId configTree, Path cfgDir) {
        if (configTree == null) {
            PathHelper.mkdirs(cfgDir);
            return;
        }

        try {
            hive.execute(new ExportTreeOperation().setSourceTree(configTree).setTargetPath(cfgDir));
        } catch (Exception e) {
            // this can happen if the hive was damaged. we allow this case to have a way out
            // if all things break badly.
            log.error("Cannot load existing configuration files", e);
        }
    }

    private void applyUpdates(List<FileStatusDto> updates, Path cfgDir) {
        for (FileStatusDto update : updates) {
            Path file = cfgDir.resolve(update.file);
            if (!file.startsWith(cfgDir)) {
                throw new WebApplicationException("Update wants to write to file outside update directory", Status.BAD_REQUEST);
            }

            try {
                switch (update.type) {
                    case ADD:
                        PathHelper.mkdirs(file.getParent());
                        Files.write(file, Base64.decodeBase64(update.content), StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.SYNC);
                        break;
                    case DELETE:
                        Files.delete(file);
                        break;
                    case EDIT:
                        Files.write(file, Base64.decodeBase64(update.content), StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
                        break;
                }
            } catch (IOException e) {
                throw new WebApplicationException("Cannot apply update to " + update.file, e);
            }
        }
    }

    private List<InstanceNodeConfigurationDto> readExistingNodeConfigs(InstanceManifest oldConfig) {
        List<InstanceNodeConfigurationDto> result = new ArrayList<>();
        for (Map.Entry<String, Manifest.Key> entry : oldConfig.getInstanceNodeManifests().entrySet()) {
            InstanceNodeManifest oldInmf = InstanceNodeManifest.of(hive, entry.getValue());
            InstanceNodeConfiguration nodeConfig = oldInmf.getConfiguration();

            InstanceNodeConfigurationDto dto = new InstanceNodeConfigurationDto(entry.getKey());
            dto.nodeConfiguration = nodeConfig;

            result.add(dto);
        }
        return result;
    }

    @WriteLock
    @Override
    public void delete(String instanceUuid) {
        Set<Key> allInstanceObjects = hive.execute(new ManifestListOperation().setManifestName(instanceUuid));
        allInstanceObjects.forEach(x -> hive.execute(new ManifestDeleteOperation().setToDelete(x)));
    }

    @Override
    public void deleteVersion(String instanceUuid, String tag) {
        Manifest.Key key = new Manifest.Key(InstanceManifest.getRootName(instanceUuid), tag);
        InstanceManifest.of(hive, key).getHistory(hive).recordAction(Action.DELETE, context.getUserPrincipal().getName(), null);
        InstanceManifest.delete(hive, key);
    }

    @Override
    public List<RemoteDirectory> getDataDirectorySnapshots(String instanceId) {
        List<RemoteDirectory> result = new ArrayList<>();

        String activeTag = getInstanceState(instanceId).activeTag;
        if (activeTag == null) {
            throw new WebApplicationException("Cannot find active version for instance " + instanceId, Status.NOT_FOUND);
        }

        InstanceStatusDto status = getStatus(instanceId);
        for (String nodeName : status.getNodesWithApps()) {
            RemoteDirectory idd = new RemoteDirectory();
            idd.minion = nodeName;
            idd.uuid = instanceId;

            try {
                MinionDto node = nodes.getNodeConfigIfOnline(nodeName);

                if (node == null) {
                    idd.problem = "Node is offline";
                } else {
                    NodeDeploymentResource sdr = ResourceProvider.getVersionedResource(node.remote, NodeDeploymentResource.class,
                            context);
                    List<RemoteDirectoryEntry> iddes = sdr.getDataDirectoryEntries(instanceId);
                    idd.entries.addAll(iddes);
                }
            } catch (Exception e) {
                log.warn("Problem fetching data directory of {}", nodeName, e);
                idd.problem = e.toString();
            }

            result.add(idd);
        }

        return result;
    }

    @Override
    public EntryChunk getEntryContent(String nodeName, RemoteDirectoryEntry entry, long offset, long limit) {
        return nodes.getNodeResourceIfOnlineOrThrow(nodeName, CommonDirectoryEntryResource.class, context).getEntryContent(entry,
                offset, limit);
    }

    @Override
    public Response getEntryStream(String nodeName, RemoteDirectoryEntry entry) {
        return nodes.getNodeResourceIfOnlineOrThrow(nodeName, CommonDirectoryEntryResource.class, context).getEntryStream(entry);
    }

    @Override
    public Response getEntriesZipSteam(String nodeName, List<RemoteDirectoryEntry> entries) {
        return nodes.getNodeResourceIfOnlineOrThrow(nodeName, CommonDirectoryEntryResource.class, context)
                .getEntriesZipStream(entries);
    }

    @Override
    public void updateDataEntries(String uuid, String nodeName, List<FileStatusDto> updates) {
        nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeDeploymentResource.class, context).updateDataEntries(uuid, updates);
    }

    @Override
    public void deleteDataEntry(String nodeName, RemoteDirectoryEntry entry) {
        nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeDeploymentResource.class, context).deleteDataEntry(entry);
    }

    @Override
    public ClientApplicationConfiguration getClientConfiguration(String uuid, String application) {
        String activeTag = getInstanceState(uuid).activeTag;
        if (activeTag == null) {
            throw new WebApplicationException("No active deployment for " + uuid, Status.NOT_FOUND);
        }

        InstanceManifest imf = InstanceManifest.load(hive, uuid, activeTag);
        InstanceGroupConfiguration groupCfg = new InstanceGroupManifest(hive).read();

        ClientApplicationConfiguration cfg = new ClientApplicationConfiguration();
        cfg.activeTag = activeTag;
        cfg.instanceGroupTitle = groupCfg.title;
        cfg.appConfig = imf.getApplicationConfiguration(hive, application);
        if (cfg.appConfig == null) {
            throw new WebApplicationException("Cannot find application " + application + " in instance " + uuid,
                    Status.NOT_FOUND);
        }
        cfg.instanceConfig = imf.getInstanceNodeConfiguration(hive, application);

        ApplicationManifest amf = ApplicationManifest.of(hive, cfg.appConfig.application);
        cfg.appDesc = amf.getDescriptor();

        // application key MUST be a ScopedManifestKey. dependencies /must/ be present
        ScopedManifestKey smk = ScopedManifestKey.parse(cfg.appConfig.application);
        cfg.resolvedRequires.addAll(
                new LocalDependencyFetcher().fetch(hive, amf.getDescriptor().runtimeDependencies, smk.getOperatingSystem()));

        // load splash screen and icon from hive and send along.
        cfg.clientSplashData = amf.readBrandingSplashScreen(hive);
        cfg.clientImageIcon = amf.readBrandingIcon(hive);

        // set dedicated config tree.
        Key clientKey = imf.getInstanceNodeManifests().get(InstanceManifest.CLIENT_NODE_NAME);
        if (clientKey != null) {
            InstanceNodeManifest inmf = InstanceNodeManifest.of(hive, clientKey);
            if (inmf != null && !inmf.getConfigTrees().isEmpty()) {
                // we either have a dedicated one, or not :)
                cfg.configTree = inmf.getConfigTrees().get(application);
            }
        }

        return cfg;
    }

    @Override
    public void logClientStart(String instanceId, String applicationId, String hostname) {
        log.debug("client start for {}, application {} on host {}", instanceId, applicationId, hostname);
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        ClientUsage clientUsage = im.getClientUsage(hive);
        ClientUsageData data = clientUsage.read();
        data.increment(applicationId, hostname);
        clientUsage.set(data);
    }

    @Override
    public ClientUsageData getClientUsage(String instanceId) {
        return InstanceManifest.load(hive, instanceId, null).getClientUsage(hive).read();
    }

    @Override
    @DeleteAfterWrite
    public Path getClientInstanceConfiguration(Manifest.Key instanceId) {
        return null; // FIXME: DCS-396: client config shall not contain server config files.
    }

    @Override
    public void start(String instanceId) {
        InstanceStatusDto status = getStatus(instanceId);

        // find all nodes where applications are deployed.
        Collection<String> nodesWithApps = status.getNodesWithApps();

        try (Activity activity = reporter.start("Starting Instance", nodesWithApps.size())) {
            for (String nodeName : nodesWithApps) {
                try {
                    nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeProcessResource.class, context).start(instanceId);
                } catch (Exception e) {
                    log.error("Cannot start {} on node {}", instanceId, nodeName, e);
                }
                activity.workAndCancelIfRequested(1);
            }
        }
    }

    @Override
    public void start(String instanceId, String applicationId) {
        start(instanceId, List.of(applicationId));
    }

    @Override
    public void start(String instanceId, List<String> applicationIds) {
        InstanceStatusDto status = getStatus(instanceId);
        Map<String, List<String>> groupedByNode = new TreeMap<>();

        for (String applicationId : applicationIds) {
            // Find minion where the application is deployed
            String nodeName = status.getNodeWhereAppIsDeployed(applicationId);
            if (nodeName == null) {
                throw new WebApplicationException("Application is not deployed on any node: " + applicationId,
                        Status.INTERNAL_SERVER_ERROR);
            }
            groupedByNode.computeIfAbsent(nodeName, n -> new ArrayList<>()).add(applicationId);
        }

        for (var entry : groupedByNode.entrySet()) {
            try (Activity activity = reporter.start("Launching " + entry.getValue().size() + " applications on " + entry.getKey(),
                    -1)) {
                // Now launch this application on the node
                nodes.getNodeResourceIfOnlineOrThrow(entry.getKey(), NodeProcessResource.class, context).start(instanceId,
                        entry.getValue());
            }
        }
    }

    @Override
    public void stop(String instanceId) {
        InstanceStatusDto status = getStatus(instanceId);

        // Find out all nodes where at least one application is running
        Collection<String> runningNodes = status.getNodesWhereAppsAreRunningOrScheduled();

        try (Activity activity = reporter.start("Stopping Instance", runningNodes.size())) {
            for (String nodeName : runningNodes) {
                try {
                    nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeProcessResource.class, context).stop(instanceId);
                } catch (Exception e) {
                    log.error("Cannot stop {} on node {}", instanceId, nodeName, e);
                }
                activity.workAndCancelIfRequested(1);
            }
        }
    }

    @Override
    public void stop(String instanceId, String applicationId) {
        stop(instanceId, List.of(applicationId));
    }

    @Override
    public void stop(String instanceId, List<String> applicationIds) {
        InstanceStatusDto status = getStatus(instanceId);
        Map<String, List<String>> groupedByNode = new TreeMap<>();

        for (var applicationId : applicationIds) {
            // Find node where the application is running
            Optional<String> node = status.node2Applications.entrySet().stream()
                    .filter(e -> e.getValue().hasApps() && e.getValue().getStatus(applicationId) != null).map(Entry::getKey)
                    .findFirst();

            if (node.isEmpty()) {
                continue; // ignore - not deployed.
            }

            groupedByNode.computeIfAbsent(node.get(), n -> new ArrayList<>()).add(applicationId);
        }

        for (var entry : groupedByNode.entrySet()) {
            // Now stop the applications on the node
            try (Activity activity = reporter.start("Stopping " + entry.getValue().size() + " applications on " + entry.getKey(),
                    -1)) {
                nodes.getNodeResourceIfOnlineOrThrow(entry.getKey(), NodeProcessResource.class, context).stop(instanceId,
                        entry.getValue());
            }
        }
    }

    @Override
    public RemoteDirectory getOutputEntry(String instanceId, String tag, String applicationId) {
        // master has the instance manifest.
        Manifest.Key instanceKey = new Manifest.Key(InstanceManifest.getRootName(instanceId), tag);
        InstanceManifest imf = InstanceManifest.of(hive, instanceKey);

        for (Map.Entry<String, Manifest.Key> entry : imf.getInstanceNodeManifests().entrySet()) {
            String nodeName = entry.getKey();
            InstanceNodeManifest inmf = InstanceNodeManifest.of(hive, entry.getValue());

            for (ApplicationConfiguration app : inmf.getConfiguration().applications) {
                if (!app.uid.equals(applicationId)) {
                    continue;
                }

                // this is our app
                RemoteDirectory id = new RemoteDirectory();
                id.minion = nodeName;
                id.uuid = instanceId;

                try {
                    RemoteDirectoryEntry oe = nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeProcessResource.class, context)
                            .getOutputEntry(instanceId, tag, applicationId);

                    if (oe != null) {
                        id.entries.add(oe);
                    }
                } catch (Exception e) {
                    log.warn("Problem fetching output entry from {} for {}, {}, {}", nodeName, instanceId, tag, applicationId, e);
                    id.problem = e.toString();
                }

                return id;
            }
        }

        throw new WebApplicationException("Cannot find application " + applicationId + " in " + instanceId + ":" + tag,
                Status.NOT_FOUND);
    }

    @Override
    public InstanceStatusDto getStatus(String instanceId) {
        // we don't use the instance manifest to figure out nodes, since node assignment can change over versions. we simply query all nodes for now.
        // TODO: this might be quite inefficient if we have many nodes, and always only deploy to a small subset.
        InstanceStatusDto instanceStatus = new InstanceStatusDto(instanceId);

        // all node names regardless of their status.
        Collection<String> nodeNames = nodes.getAllNodeNames();

        try (Activity activity = reporter.start("Read Node Processes", nodeNames.size())) {
            for (String nodeName : nodeNames) {
                MinionDto node = nodes.getNodeConfigIfOnline(nodeName);

                if (node == null) {
                    continue; // don't log to avoid flooding - node manager will log once.
                }

                NodeProcessResource spc = ResourceProvider.getVersionedResource(node.remote, NodeProcessResource.class, context);
                try {
                    InstanceNodeStatusDto nodeStatus = spc.getStatus(instanceId);
                    instanceStatus.add(nodeName, nodeStatus);
                } catch (Exception e) {
                    log.error("Cannot fetch process status of {}", nodeName);
                    if (log.isDebugEnabled()) {
                        log.debug("Exception:", e);
                    }
                }
                activity.worked(1);
            }
        }
        return instanceStatus;
    }

    @Override
    public ProcessDetailDto getProcessDetails(String instanceId, String appUid) {
        // Check if the application is running on a node
        InstanceStatusDto status = getStatus(instanceId);
        String nodeName = status.getNodeWhereAppIsRunning(appUid);

        // Check if the application is deployed on a node
        if (nodeName == null) {
            nodeName = status.getNodeWhereAppIsDeployed(appUid);
        }

        // Application is nowhere deployed and nowhere running
        if (nodeName == null) {
            return null;
        }

        // Query process details
        try {
            return nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeProcessResource.class, context)
                    .getProcessDetails(instanceId, appUid);
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Cannot fetch process status from " + nodeName + " for " + instanceId + ", " + appUid, e);
        }
    }

    @Override
    public String generateWeakToken(String principal) {
        return root.createWeakToken(principal);
    }

    @Override
    public void writeToStdin(String instanceId, String applicationId, String data) {
        InstanceStatusDto status = getStatus(instanceId);
        String nodeName = status.getNodeWhereAppIsRunning(applicationId);
        if (nodeName == null) {
            throw new WebApplicationException("Application is not running on any node.", Status.INTERNAL_SERVER_ERROR);
        }

        nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeProcessResource.class, context).writeToStdin(instanceId, applicationId,
                data);
    }

    @Override
    public Map<Integer, Boolean> getPortStates(String nodeName, List<Integer> ports) {
        return nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeDeploymentResource.class, context).getPortStates(ports);
    }

    @Override
    public MasterRuntimeHistoryDto getRuntimeHistory(String instanceId) {
        // we don't use the instance manifest to figure out nodes, since node assignment can change over versions. we simply query all nodes for now.
        // TODO: this might be quite inefficient if we have many nodes, and always only deploy to a small subset.

        MasterRuntimeHistoryDto history = new MasterRuntimeHistoryDto();

        // all node names regardless of their status.
        Collection<String> nodeNames = nodes.getAllNodeNames();

        for (String nodeName : nodeNames) {
            try {
                history.add(nodeName, nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeProcessResource.class, context)
                        .getRuntimeHistory(instanceId));
            } catch (Exception e) {
                history.addError(nodeName, "Cannot load runtime history (" + e.getMessage() + ")");
            }
        }
        return history;
    }

    @Override
    public InstanceBannerRecord getBanner(String instanceId) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        return im.getBanner(hive).read();
    }

    @Override
    public void updateBanner(String instanceId, InstanceBannerRecord instanceBannerRecord) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        im.getBanner(hive).set(instanceBannerRecord);

        im.getHistory(hive).recordAction(instanceBannerRecord.text != null ? Action.BANNER_SET : Action.BANNER_CLEAR,
                context.getUserPrincipal().getName(), null);
    }

    @Override
    public CustomAttributesRecord getAttributes(String instanceId) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        return im.getAttributes(hive).read();
    }

    @Override
    public void updateAttributes(String instanceId, CustomAttributesRecord attributes) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        im.getAttributes(hive).set(attributes);
    }

    @Override
    public void updateOverallStatus() {
        SortedSet<Key> imKeys = InstanceManifest.scan(hive, true);
        Map<String, MinionStatusDto> nodeStatus = nodes.getAllNodeStatus();

        try (Activity all = reporter.start("Fetch Instance Status", imKeys.size())) {
            for (Key imKey : imKeys) {
                InstanceManifest im = InstanceManifest.of(hive, imKey);

                if (im.getState(hive).read().activeTag == null) {
                    continue; // no active tag means there cannot be any status.
                }

                InstanceConfiguration config = im.getConfiguration();

                // get all node status of the responsible master.
                InstanceStatusDto processStatus = getStatus(config.uuid);
                List<InstanceNodeConfigurationDto> nodeConfigs = readExistingNodeConfigs(im);

                List<String> stoppedApps = new ArrayList<>();
                List<String> runningApps = new ArrayList<>();

                OverallStatus overallStatus = OverallStatus.RUNNING;
                List<String> overallStatusMessages = new ArrayList<>();

                for (var nodeCfg : nodeConfigs) {
                    if (InstanceManifest.CLIENT_NODE_NAME.equals(nodeCfg.nodeName)) {
                        continue; // don't check client.
                    }

                    MinionStatusDto state = nodeStatus.get(nodeCfg.nodeName);

                    if (state == null || state.offline) {
                        overallStatus = InstanceOverallStateRecord.OverallStatus.WARNING;
                        overallStatusMessages.add("Node " + nodeCfg.nodeName + " is not available");
                        continue;
                    }

                    InstanceNodeStatusDto statusOnNode = processStatus.node2Applications.get(nodeCfg.nodeName);

                    for (var app : nodeCfg.nodeConfiguration.applications) {
                        if (app.processControl.startType != ApplicationStartType.INSTANCE) {
                            continue;
                        }

                        if (!statusOnNode.isAppDeployed(app.uid)) {
                            log.warn("Expected application is not currently deployed: {}", app.uid);
                            continue;
                        }

                        // instance application, check status
                        ProcessStatusDto status = statusOnNode.getStatus(app.uid);
                        if (status.processState.isStopped()) {
                            stoppedApps.add(app.name);
                        } else {
                            runningApps.add(app.name);
                        }
                    }
                }

                if (stoppedApps.isEmpty() && runningApps.isEmpty()) {
                    // this means that ther are no instance type applications on the instance.
                    if (overallStatus != OverallStatus.WARNING) {
                        overallStatus = OverallStatus.STOPPED;
                    }
                } else if (stoppedApps.isEmpty() || runningApps.isEmpty()) {
                    // valid - either all stopped or all running.
                    if (overallStatus != OverallStatus.WARNING) {
                        overallStatus = runningApps.isEmpty() ? OverallStatus.STOPPED : OverallStatus.RUNNING;
                    }
                } else {
                    // not ok, some apps started, some stopped - that will be a warning.
                    overallStatus = OverallStatus.WARNING;
                    overallStatusMessages.add(stoppedApps.size() + " instance type applications are not running.");
                }

                im.getOverallState(hive).update(overallStatus, overallStatusMessages);
                all.workAndCancelIfRequested(1);
            }
        }
    }

    @Override
    public Response getConfigZipSteam(String instanceId, String application) {
        InstanceStateRecord state = getInstanceState(instanceId);
        if (state.activeTag == null) {
            throw new WebApplicationException("Instance has no active tag: " + instanceId, Status.NOT_FOUND);
        }

        InstanceManifest imf = InstanceManifest.load(hive, instanceId, state.activeTag);
        Manifest.Key key = imf.getInstanceNodeManifests().get(InstanceManifest.CLIENT_NODE_NAME); // only for clients.

        if (key == null) {
            throw new WebApplicationException("Instance has no client node: " + instanceId, Status.NOT_FOUND);
        }

        InstanceNodeManifest inmf = InstanceNodeManifest.of(hive, key);
        ObjectId configTree = inmf.getConfigTrees().get(application);

        if (configTree == null) {
            throw new WebApplicationException(
                    "Application " + application + " in instance " + instanceId + " does not have config files",
                    Status.NOT_FOUND);
        }

        // Build a response with the stream
        var responseBuilder = Response.ok(new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException {
                zipConfigTree(output, configTree);
            }
        });

        // Load and attach metadata to give the file a nice name
        var contentDisposition = ContentDisposition.type("attachement").fileName("DataFiles.zip").build();
        responseBuilder.header("Content-Disposition", contentDisposition);
        return responseBuilder.build();
    }

    private void zipConfigTree(OutputStream output, ObjectId configTree) {
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory(root.getTempDir(), "cfgUp-");
            Path cfgDir = tmpDir.resolve("cfg");

            // 1. export current tree to temp directory
            exportConfigTree(configTree, cfgDir);

            // 2. create ZIP stream.
            ZipHelper.zip(output, cfgDir);
        } catch (IOException e) {
            throw new WebApplicationException("Cannot update configuration files", e);
        } finally {
            if (tmpDir != null) {
                PathHelper.deleteRecursive(tmpDir);
            }
        }
    }

}
