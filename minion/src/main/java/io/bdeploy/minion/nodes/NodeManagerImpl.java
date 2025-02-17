package io.bdeploy.minion.nodes;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.NamedDaemonThreadFactory;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManager;
import io.bdeploy.ui.api.impl.ChangeEventManager;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeHint;
import io.bdeploy.ui.dto.ObjectChangeType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

@Service
public class NodeManagerImpl implements NodeManager, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NodeManagerImpl.class);

    private MinionRoot root;
    private String self;
    private MinionConfiguration config;

    private ChangeEventManager changes;

    private final Map<String, Boolean> contactWarning = new ConcurrentHashMap<>();
    private final Map<String, MinionStatusDto> status = new ConcurrentHashMap<>();
    private final ScheduledExecutorService schedule = Executors
            .newSingleThreadScheduledExecutor(new NamedDaemonThreadFactory("Scheduled Node Update"));

    private final AtomicLong contactNumber = new AtomicLong(0);
    private final ExecutorService contact = Executors
            .newCachedThreadPool(new NamedDaemonThreadFactory(() -> "Node Contact " + contactNumber.incrementAndGet()));
    private final Map<String, Future<?>> requests = new TreeMap<>();

    private ScheduledFuture<?> saveJob;

    public void initialize(MinionRoot root, boolean initialFetch) {
        this.root = root;
        this.config = new MinionManifest(root.getHive()).read();
        this.self = root.getState().self;

        // initially, all nodes are offline.
        this.config.entrySet().forEach(e -> this.status.put(e.getKey(), createStarting(e.getValue())));

        if (root.getMode() == MinionMode.CENTRAL) {
            // no need to periodically fetch states here. However we *do* want to verify connectivity
            // to our own backend once. This is required for things like log file fetching, etc.
            initialFetchNodeStates();
            return;
        }

        // initially, all nodes are marked as "warn on contact failure".
        this.config.entrySet().forEach(e -> this.contactWarning.put(e.getKey(), Boolean.TRUE));
        this.schedule.scheduleAtFixedRate(this::fetchNodeStates, 0, 10, TimeUnit.SECONDS);

        if (initialFetch) {
            log.info("Synchronous initial state fetching in Node Manager...");
            // since this delays startup for synchronous state fetching, we only want this in tests.
            initialFetchNodeStates();

            log.info("... done");
        }
    }

    @Override
    public void close() {
        schedule.shutdownNow();
        contact.shutdownNow();
    }

    private void initialFetchNodeStates() {
        fetchNodeStates();
        this.requests.forEach((n, r) -> {
            try {
                r.get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Unexpected exception on initial node contact", ie);
            } catch (Exception e) {
                // should never happen
                log.error("Unexpected exception on initial node contact", e);
            }
        });
    }

    private MinionStatusDto createStarting(MinionDto node) {
        return MinionStatusDto.createOffline(node, "Starting...");
    }

    private void fetchNodeStates() {
        if (log.isDebugEnabled()) {
            log.debug("Fetch status of {} minions", config.values().size());
        }

        for (String minion : config.values().keySet()) {
            // fetch an existing request.
            var existing = requests.get(minion);

            if (existing != null && !existing.isDone()) {
                // something is already running - we don't start another one.
                // best case: it finishes "soon" - worst case it is "stuck"
                if (log.isDebugEnabled()) {
                    log.debug("Status request to {} still running", minion);
                }
                continue;
            }

            // start async request to a single minion.
            requests.put(minion, contact.submit(() -> fetchNodeState(minion)));
        }
    }

    /**
     * @param node the minion to contact. The state is recorded in the status map.
     */
    private void fetchNodeState(String node) {
        MinionDto mdto = config.getMinion(node);
        try {

            if (log.isDebugEnabled()) {
                log.debug("Contacting node {}", node);
            }

            // in case the configuration was removed while we were scheduled.
            if (mdto != null) {
                MinionStatusDto msd = ResourceProvider.getResource(mdto.remote, MinionStatusResource.class, null).getStatus();
                status.put(node, msd);

                // previously inhibited contact warning means node was not reachable. log recovery
                if (Boolean.FALSE.equals(contactWarning.get(node))) {
                    log.info("Node {} connection recovered", node);
                    if (changes != null) {
                        changes.change(ObjectChangeType.NODES,
                                Map.of(ObjectChangeDetails.NODE, node, ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE));
                    }
                }

                contactWarning.put(node, Boolean.TRUE);

                if (log.isDebugEnabled()) {
                    log.debug("Node {} contacted successfully, offline={}, version={}, info={}", node, msd.offline,
                            msd.config == null ? "unknown" : msd.config.version, msd.infoText);
                }

                // we compare whether some relevant information has changed and schedule saving in case it has.
                if (!VersionHelper.equals(mdto.version, msd.config.version)) {
                    mdto.version = msd.config.version;
                    scheduleSave(); // schedule immediate when configuration changes.
                }
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to contact {}", node, e);
            }

            status.put(node, MinionStatusDto.createOffline(mdto, e.toString()));

            // log it, we don't want to hold status in the futures.
            if (Boolean.TRUE.equals(contactWarning.get(node))) {
                contactWarning.put(node, Boolean.FALSE); // no warning, contact failed.
                log.warn("Failed to fetch node {} status: {}", node, e.toString());
                if (changes != null) {
                    changes.change(ObjectChangeType.NODES,
                            Map.of(ObjectChangeDetails.NODE, node, ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE));
                }
            }
        }
    }

    @Override
    public Map<String, MinionDto> getAllNodes() {
        return Collections.unmodifiableMap(config.values());
    }

    @Override
    public Collection<String> getAllNodeNames() {
        return Collections.unmodifiableSet(config.values().keySet());
    }

    @Override
    public Map<String, MinionStatusDto> getAllNodeStatus() {
        return Collections.unmodifiableMap(status);
    }

    @Override
    public MinionDto getNodeConfig(String name) {
        return config.getMinion(name);
    }

    @Override
    public MinionStatusDto getNodeStatus(String name) {
        return status.get(name);
    }

    @Override
    public MinionDto getNodeConfigIfOnline(String name) {
        var state = status.get(name);
        if (state == null || state.offline) {
            if (requests.containsKey(name)) {
                // a background connection request is running.
                // we do not want to block for an extended time here, however this might come
                // with extremely unpleasant timing, so we wait a small amount of time in case
                // the minion is responsive.
                var rq = requests.get(name);
                try {
                    rq.get(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Waiting for node {} failed: {}", name, ie.toString());
                    return null;
                } catch (Exception e) {
                    log.warn("Waiting for node {} failed: {}", name, e.toString());
                    return null;
                }

                // refresh state if we successfully awaited the request.
                state = status.get(name);
                if (state == null || state.offline) {
                    // still offline after we tried to contact it.
                    return null;
                }
            } else {
                // no request running, this is simply offline for *some* reason.
                return null;
            }
        }
        return state.config;
    }

    @Override
    public <T> T getNodeResourceIfOnlineOrThrow(String minion, Class<T> clazz, SecurityContext context) {
        MinionDto node = getNodeConfigIfOnline(minion);
        if (node == null) {
            throw new WebApplicationException("Node not available " + minion, Status.EXPECTATION_FAILED);
        }
        return ResourceProvider.getVersionedResource(node.remote, clazz, context);
    }

    @Override
    public MinionDto getSelf() {
        return config.getMinion(self);
    }

    @Override
    public String getSelfName() {
        return self;
    }

    private synchronized void scheduleSave() {
        // in case there was one scheduled - cancel it and reschedule.
        if (saveJob != null && !saveJob.isDone()) {
            saveJob.cancel(false);
        }

        // schedule saving after a short timeout to avoid spamming saves.
        saveJob = schedule.schedule(() -> {
            MinionManifest mm = new MinionManifest(root.getHive());
            mm.update(config);
        }, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void addNode(String name, MinionDto minion) {
        log.info("Adding node {}", name);

        config.addMinion(name, minion);
        status.put(name, createStarting(minion));
        contactWarning.put(name, Boolean.FALSE); // was not reachable (new), issue recovery log.
        scheduleSave();

        log.info("Updating state for added node {}", name);
        fetchNodeState(name);
    }

    @Override
    public void editNode(String name, RemoteService node) {
        log.info("Editing node {}", name);

        MinionDto m = config.getMinion(name);
        m.remote = node;
        scheduleSave();

        log.info("Updating state for edited node {}", name);
        fetchNodeState(name);
    }

    @Override
    public void removeNode(String name) {
        log.info("Removing node {}", name);

        config.removeMinion(name);
        status.remove(name);
        contactWarning.remove(name);
        scheduleSave();
    }

    @Override
    public void setChangeEventManager(ChangeEventManager changes) {
        this.changes = changes;
    }

}
