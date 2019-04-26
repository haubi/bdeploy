package io.bdeploy.minion.remote.jersey;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.dependencies.LocalDependencyFetcher;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.remote.SlaveDeploymentResource;
import io.bdeploy.interfaces.remote.SlaveProcessResource;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.MinionState;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;

public class MasterNamedResourceImpl implements MasterNamedResource {

    private static final Logger log = LoggerFactory.getLogger(MasterNamedResourceImpl.class);

    private final BHive hive;
    private final ActivityReporter reporter;
    private final MinionRoot root;

    public MasterNamedResourceImpl(MinionRoot root, BHive hive, ActivityReporter reporter) {
        this.root = root;
        this.hive = hive;
        this.reporter = reporter;
    }

    @Override
    public void install(Key key) {
        InstanceManifest imf = InstanceManifest.of(hive, key);
        SortedMap<String, Key> fragmentReferences = imf.getInstanceNodeManifests();

        try (Activity deploying = reporter.start("Deploying to minions...", fragmentReferences.size())) {
            for (Map.Entry<String, Manifest.Key> entry : fragmentReferences.entrySet()) {
                String minionName = entry.getKey();
                if (InstanceNodeConfigurationListDto.CLIENT_NODE_NAME.equals(minionName)) {
                    continue;
                }
                Manifest.Key toDeploy = entry.getValue();
                RemoteService minion = root.getState().minions.get(minionName);

                assertNotNull(minion, "Cannot lookup minion on master: " + minionName);
                assertNotNull(toDeploy, "Cannot lookup minion manifest on master: " + toDeploy);

                // make sure the minion has the manifest.
                hive.execute(new PushOperation().setRemote(minion).addManifest(toDeploy));

                SlaveDeploymentResource deployment = ResourceProvider.getResource(minion, SlaveDeploymentResource.class);
                try {
                    deployment.install(toDeploy);
                } catch (Exception e) {
                    throw new WebApplicationException("Cannot deploy to " + minionName, e, Status.INTERNAL_SERVER_ERROR);
                }

                deploying.worked(1);
            }
        }
    }

    @Override
    public void activate(Key key) {
        InstanceManifest imf = InstanceManifest.of(hive, key);

        if (!isFullyDeployed(imf)) {
            throw new WebApplicationException(
                    "Given manifest for UUID " + imf.getConfiguration().uuid + " is not fully deployed: " + key,
                    Status.NOT_FOUND);
        }

        SortedMap<String, Key> fragments = imf.getInstanceNodeManifests();
        try (Activity activating = reporter.start("Activating on minions...", fragments.size())) {
            for (Map.Entry<String, Manifest.Key> entry : fragments.entrySet()) {
                String minionName = entry.getKey();
                if (InstanceNodeConfigurationListDto.CLIENT_NODE_NAME.equals(minionName)) {
                    continue;
                }
                Manifest.Key toDeploy = entry.getValue();
                RemoteService minion = root.getState().minions.get(minionName);

                assertNotNull(minion, "Cannot lookup minion on master: " + minionName);
                assertNotNull(toDeploy, "Cannot lookup minion manifest on master: " + toDeploy);

                SlaveDeploymentResource deployment = ResourceProvider.getResource(minion, SlaveDeploymentResource.class);
                try {
                    deployment.activate(toDeploy);
                } catch (Exception e) {
                    throw new WebApplicationException("Cannot activate on " + minionName, e, Status.INTERNAL_SERVER_ERROR);
                }

                activating.worked(1);
            }
        }

        // TODO: don't record this in the master state. Record in BHive instead. IMPORTANT as hive must be self-contained.
        // record the master manifest as deployed.
        root.modifyState(s -> s.activeMasterVersions.put(imf.getConfiguration().uuid, key));
    }

    /**
     * @param imf the {@link InstanceManifest} to check.
     * @return whether the given {@link InstanceManifest} is fully deployed to all required minions.
     */
    private boolean isFullyDeployed(InstanceManifest imf) {
        SortedMap<String, Key> imfs = imf.getInstanceNodeManifests();
        // No configuration -> cannot be deployed
        if (imfs.isEmpty()) {
            return false;
        }
        // check all minions for their respective availability.
        for (Map.Entry<String, Manifest.Key> entry : imfs.entrySet()) {
            String minionName = entry.getKey();
            if (InstanceNodeConfigurationListDto.CLIENT_NODE_NAME.equals(minionName)) {
                continue;
            }
            Manifest.Key toDeploy = entry.getValue();
            RemoteService minion = root.getState().minions.get(minionName);

            assertNotNull(minion, "Cannot lookup minion on master: " + minionName);
            assertNotNull(toDeploy, "Cannot lookup minion manifest on master: " + toDeploy);

            SlaveDeploymentResource deployment = ResourceProvider.getResource(minion, SlaveDeploymentResource.class);
            SortedMap<String, SortedSet<Key>> uuidMapped = deployment.getAvailableDeployments();
            if (!uuidMapped.containsKey(imf.getConfiguration().uuid)) {
                log.debug("Minion " + minionName + " does not contain any deployment for " + imf.getConfiguration().uuid);
                return false;
            }

            if (!uuidMapped.get(imf.getConfiguration().uuid).contains(toDeploy)) {
                log.debug("Minion " + minionName + " does not have " + toDeploy + " available");
                return false;
            }
        }
        return true;
    }

    @Override
    public void remove(Key key) {
        InstanceManifest imf = InstanceManifest.of(hive, key);

        root.modifyState(s -> {
            if (key.equals(s.activeMasterVersions.get(imf.getConfiguration().uuid))) {
                log.warn("Removing active version for " + imf.getConfiguration().uuid);
                s.activeMasterVersions.remove(imf.getConfiguration().uuid);
            }
        });

        SortedMap<String, Key> fragments = imf.getInstanceNodeManifests();
        Activity removing = reporter.start("Removing on minions...", fragments.size());

        try {
            for (Map.Entry<String, Manifest.Key> entry : fragments.entrySet()) {
                String minionName = entry.getKey();
                if (InstanceNodeConfigurationListDto.CLIENT_NODE_NAME.equals(minionName)) {
                    continue;
                }
                Manifest.Key toRemove = entry.getValue();
                RemoteService minion = root.getState().minions.get(minionName);

                assertNotNull(minion, "Cannot lookup minion on master: " + minionName);
                assertNotNull(toRemove, "Cannot lookup minion manifest on master: " + toRemove);

                SlaveDeploymentResource deployment = ResourceProvider.getResource(minion, SlaveDeploymentResource.class);
                try {
                    deployment.remove(toRemove);
                } catch (Exception e) {
                    throw new WebApplicationException("Cannot remove on " + minionName, e);
                }

                removing.worked(1);
            }
        } finally {
            removing.done();
        }

        // no need to clean up the hive, this is done elsewhere.
    }

    @Override
    public SortedMap<String, SortedSet<Key>> getAvailableDeployments() {
        SortedSet<Key> scan = InstanceManifest.scan(hive, false);
        SortedMap<String, SortedSet<Key>> uuidMapped = new TreeMap<>();

        for (Manifest.Key k : scan) {
            InstanceManifest imf = InstanceManifest.of(hive, k);
            try {
                if (!isFullyDeployed(imf)) {
                    continue;
                }
            } catch (Exception e) {
                log.warn("Cannot check deployment state of: " + imf.getManifest());
                continue;
            }

            uuidMapped.computeIfAbsent(imf.getConfiguration().uuid, y -> new TreeSet<>()).add(imf.getManifest());
        }

        return uuidMapped;
    }

    @Override
    public SortedMap<String, Key> getActiveDeployments() {
        return root.getState().activeMasterVersions;
    }

    @Override
    public SortedMap<String, SortedSet<Key>> getAvailableDeployments(String minion) {
        RemoteService m = root.getState().minions.get(minion);
        assertNotNull(m, "Cannot find minion " + minion);

        SlaveDeploymentResource client = ResourceProvider.getResource(m, SlaveDeploymentResource.class);
        try {
            return client.getAvailableDeployments();
        } catch (Exception e) {
            throw new WebApplicationException("Cannot read deployments from minion " + minion, e, Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public SortedMap<String, Key> getActiveDeployments(String minion) {
        RemoteService m = root.getState().minions.get(minion);
        assertNotNull(m, "Cannot find minion " + minion);

        SlaveDeploymentResource client = ResourceProvider.getResource(m, SlaveDeploymentResource.class);
        try {
            return client.getActiveDeployments();
        } catch (Exception e) {
            throw new WebApplicationException("Cannot read deployments from minion " + minion, e, Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ClientApplicationConfiguration getClientConfiguration(String uuid, String application) {
        Manifest.Key active = getActiveDeployments().get(uuid);

        if (active == null) {
            throw new WebApplicationException("No active deployment for " + uuid, Status.NOT_FOUND);
        }

        InstanceManifest imf = InstanceManifest.of(hive, active);
        ClientApplicationConfiguration cfg = new ClientApplicationConfiguration();

        // find the application with the given ID in the configuration. Don't rely on naming magic, just check all nodes, it's not so expensive.
        for (Map.Entry<String, Manifest.Key> entry : imf.getInstanceNodeManifests().entrySet()) {
            InstanceNodeManifest inmf = InstanceNodeManifest.of(hive, entry.getValue());
            for (ApplicationConfiguration app : inmf.getConfiguration().applications) {
                if (app.uid.equals(application)) {
                    // this is the one.
                    cfg.clientConfig = app;
                }
            }
        }

        if (cfg.clientConfig == null) {
            throw new WebApplicationException("Cannot find application " + application + " in instance " + uuid,
                    Status.NOT_FOUND);
        }

        ApplicationManifest amf = ApplicationManifest.of(hive, cfg.clientConfig.application);
        cfg.clientDesc = amf.getDescriptor();
        cfg.instanceKey = active;

        // application key MUST be a ScopedManifestKey. dependencies /must/ be present
        ScopedManifestKey smk = ScopedManifestKey.parse(cfg.clientConfig.application);
        cfg.resolvedRequires.addAll(
                new LocalDependencyFetcher().fetch(hive, amf.getDescriptor().runtimeDependencies, smk.getOperatingSystem()));

        return cfg;
    }

    @Override
    public void start(String instanceId) {
        SortedMap<String, RemoteService> minions = root.getState().minions;
        InstanceStatusDto status = getStatus(instanceId);
        for (String nodeName : status.getNodesWithApps()) {
            RemoteService service = minions.get(nodeName);
            SlaveProcessResource spc = ResourceProvider.getResource(service, SlaveProcessResource.class);
            spc.start(instanceId);
        }
    }

    @Override
    public void start(String instanceId, String applicationId) {
        // Check if this version is already running on a node
        InstanceStatusDto status = getStatus(instanceId);
        if (status.isAppRunning(applicationId)) {
            String node = status.getNodeWhereAppIsRunningOrScheduled(applicationId);
            throw new WebApplicationException("Application is already running on node '" + node + "'.",
                    Status.INTERNAL_SERVER_ERROR);
        }

        // Find minion where the application is deployed
        String minion = status.getNodeWhereAppIsDeployedInActiveVersion(applicationId);
        if (minion == null) {
            throw new WebApplicationException("Application is not deployed on any node.", Status.INTERNAL_SERVER_ERROR);
        }

        // Now launch this application on the minion
        try (Activity activity = reporter.start("Starting application...", -1)) {
            SortedMap<String, RemoteService> minions = root.getState().minions;
            RemoteService service = minions.get(minion);
            SlaveProcessResource spc = ResourceProvider.getResource(service, SlaveProcessResource.class);
            spc.start(instanceId, applicationId);
        }
    }

    @Override
    public void stop(String instanceId) {
        InstanceStatusDto status = getStatus(instanceId);
        SortedMap<String, RemoteService> minions = root.getState().minions;

        // Find out all nodes where at least one application is running
        Collection<String> nodes = status.getNodesWhereAppsAreRunningOrScheduled();

        try (Activity activity = reporter.start("Stopping applications...", nodes.size())) {
            for (String node : nodes) {
                RemoteService service = minions.get(node);
                SlaveProcessResource spc = ResourceProvider.getResource(service, SlaveProcessResource.class);
                spc.stop(instanceId);
                activity.worked(1);
            }
        }
    }

    @Override
    public void stop(String instanceId, String applicationId) {
        // Find out where the application is running on
        InstanceStatusDto status = getStatus(instanceId);
        if (!status.isAppRunningOrScheduled(applicationId)) {
            throw new WebApplicationException("Application is not running on any node.", Status.INTERNAL_SERVER_ERROR);
        }
        String nodeName = status.getNodeWhereAppIsRunningOrScheduled(applicationId);

        // Now stop this application on the minion
        try (Activity activity = reporter.start("Stopping application...", -1)) {
            SortedMap<String, RemoteService> minions = root.getState().minions;
            RemoteService service = minions.get(nodeName);
            SlaveProcessResource spc = ResourceProvider.getResource(service, SlaveProcessResource.class);
            spc.stop(instanceId, applicationId);
        }
    }

    @Override
    public InstanceStatusDto getStatus(String instanceId) {
        InstanceStatusDto instanceStatus = new InstanceStatusDto(instanceId);

        SortedMap<String, RemoteService> minions = root.getState().minions;
        try (Activity activity = reporter.start("Get node status...", minions.size())) {
            for (Entry<String, RemoteService> entry : minions.entrySet()) {
                String minion = entry.getKey();
                SlaveProcessResource spc = ResourceProvider.getResource(entry.getValue(), SlaveProcessResource.class);
                try {
                    InstanceNodeStatusDto nodeStatus = spc.getStatus(instanceId);
                    instanceStatus.add(minion, nodeStatus);
                } catch (Exception e) {
                    log.error("Cannot fetch process status of " + minion);
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
    public String generateWeakToken(String principal) {
        ApiAccessToken token = new ApiAccessToken.Builder().setIssuedTo(principal).setWeak(true).build();
        SecurityHelper sh = SecurityHelper.getInstance();

        MinionState state = root.getState();
        KeyStore ks;
        try {
            ks = sh.loadPrivateKeyStore(state.keystorePath, state.keystorePass);
        } catch (GeneralSecurityException | IOException e) {
            throw new WebApplicationException("Cannot generate weak token", e);
        }

        try {
            return SecurityHelper.getInstance().createSignaturePack(token, ks, state.keystorePass);
        } catch (Exception e) {
            throw new WebApplicationException("Cannot create weak token", e);
        }
    }

}
