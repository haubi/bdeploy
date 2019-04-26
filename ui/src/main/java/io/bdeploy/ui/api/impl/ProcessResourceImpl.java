package io.bdeploy.ui.api.impl;

import java.util.Map;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.ProcessResource;

public class ProcessResourceImpl implements ProcessResource {

    private final BHive hive;
    private final String instanceGroup;
    private final String instanceId;

    public ProcessResourceImpl(BHive hive, String instanceGroup, String instanceId) {
        this.hive = hive;
        this.instanceGroup = instanceGroup;
        this.instanceId = instanceId;
    }

    @Override
    public ProcessStatusDto getStatus(String processId) {
        MasterNamedResource master = getMasterResource();
        InstanceStatusDto instanceStatus = master.getStatus(instanceId);
        return instanceStatus.getAppStatus(processId);
    }

    @Override
    public Map<String, ProcessStatusDto> getStatus() {
        MasterNamedResource master = getMasterResource();
        InstanceStatusDto instanceStatus = master.getStatus(instanceId);
        return instanceStatus.getAppStatus();
    }

    @Override
    public void startProcess(String processId) {
        MasterNamedResource master = getMasterResource();
        master.start(instanceId, processId);
    }

    @Override
    public void stopProcess(String processId) {
        MasterNamedResource master = getMasterResource();
        master.stop(instanceId, processId);
    }

    @Override
    public void restartProcess(String processId) {
        MasterNamedResource master = getMasterResource();
        master.stop(instanceId, processId);
        master.start(instanceId, processId);
    }

    @Override
    public void startAll() {
        MasterNamedResource master = getMasterResource();
        master.start(instanceId);
    }

    @Override
    public void stopAll() {
        MasterNamedResource master = getMasterResource();
        master.stop(instanceId);
    }

    @Override
    public void restart() {
        stopAll();
        startAll();
    }

    private MasterNamedResource getMasterResource() {
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, null);
        RemoteService remote = manifest.getConfiguration().target;
        MasterRootResource root = ResourceProvider.getResource(remote, MasterRootResource.class);
        return root.getNamedMaster(instanceGroup);
    }

}
