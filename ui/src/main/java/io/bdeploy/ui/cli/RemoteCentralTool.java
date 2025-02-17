package io.bdeploy.ui.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.Version;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.NonExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.cli.RemoteCentralTool.CentralConfig;
import io.bdeploy.ui.dto.BackendInfoDto;
import io.bdeploy.ui.dto.MinionSyncResultDto;
import jakarta.ws.rs.WebApplicationException;

@Help("Manage attached managed servers on central server")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-central")
public class RemoteCentralTool extends RemoteServiceTool<CentralConfig> {

    public @interface CentralConfig {

        @Help(value = "Download managed server identification information from target MANAGED server.", arg = false)
        boolean managedIdent() default false;

        @Help("The target file to write managed or central identification information to")
        @Validator(NonExistingPathValidator.class)
        String output();

        @Help("Attach a managed server with the given server identification information (file) to the target CENTRAL server.")
        String attach();

        @Help("The instance group to query/manipulate managed servers for on the target CENTRAL server.")
        String instanceGroup();

        @Help("Override the name of the target managed server when attaching.")
        String name();

        @Help("Set the description of the managed server being attached")
        String description();

        @Help("Override the URI of the managed server being attached. The URI must be reachable from the central server.")
        String uri();

        @Help(value = "Don't try to automatically attach the managed server from the target CENTRAL server. Rather output a file which can be uploaded to the managed server.",
              arg = false)
        boolean offline() default false;

        @Help("Path to a file output by the central server in offline mode to be used on the target MANAGED server.")
        @Validator(ExistingPathValidator.class)
        String attachCentral();

        @Help(value = "List existing attached managed servers for an instance group on the target CENTRAL server.", arg = false)
        boolean list() default false;

        @Help(value = "Synchronize with the given managed server in the given instance group on the target CENTRAL server.",
              arg = false)
        boolean synchronize() default false;

        @Help(value = "Remove the given managed server in the given instance group on the target CENTRAL server. Also removes all instances associated with the local server from the central (but not from the managed server).",
              arg = false)
        boolean delete() default false;

        @Help(value = "Try to establish a connection to the given managed server and print its version.", arg = false)
        boolean ping() default false;

        @Help("The existing managed server attached to the instance group to query/manipulate")
        String server();

        @Help("Update the specified server. Use the --auth, --uri and --description parameters to update values.")
        String update();

        @Help("When updating a managed server, update the authentication token to the given value. EXPERT only!")
        String auth();
    }

    public RemoteCentralTool() {
        super(CentralConfig.class);
    }

    @Override
    protected RenderableResult run(CentralConfig config, RemoteService remote) {
        BackendInfoResource bir = ResourceProvider.getVersionedResource(remote, BackendInfoResource.class, getLocalContext());
        BackendInfoDto version = bir.getVersion();

        if (config.managedIdent()) {
            return getManagedServerIdent(config, bir, version);
        }

        ManagedServersResource msr = ResourceProvider.getVersionedResource(remote, ManagedServersResource.class,
                getLocalContext());

        if (config.attachCentral() != null) {
            return attachCentralServer(config, version, msr);
        }

        // the rest of the commands are central only.
        checkMode(version, MinionMode.CENTRAL);

        if (config.list()) {
            return listManagedServers(config, remote, msr);
        } else if (config.attach() != null) {
            return attachManagedServer(config, msr);
        } else if (config.synchronize()) {
            helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");
            helpAndFailIfMissing(config.server(), "Missing --server");

            MinionSyncResultDto result = msr.synchronize(config.instanceGroup(), config.server());
            return createSuccess().addField("Managed Server", config.server()).addField("Running Version",
                    result.server.update.runningVersion);
        } else if (config.delete()) {
            helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");
            helpAndFailIfMissing(config.server(), "Missing --server");

            msr.deleteManagedServer(config.instanceGroup(), config.server());

            return createSuccess().addField("Removed Managed Server", config.server());
        } else if (config.ping()) {
            helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");
            helpAndFailIfMissing(config.server(), "Missing --server");

            try {
                long start = System.currentTimeMillis();
                Version v = msr.pingServer(config.instanceGroup(), config.server());
                return createSuccess().addField("Server Version", v).addField("Full Roundtrip Time",
                        (System.currentTimeMillis() - start) + "ms");
            } catch (WebApplicationException e) {
                return createResultWithMessage("Could not contact server " + config.server()).setException(e);
            }
        } else if (config.update() != null) {
            return updateManagedServer(config, msr);
        } else {
            return createNoOp();
        }
    }

    private DataResult updateManagedServer(CentralConfig config, ManagedServersResource msr) {
        helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");

        String uri = config.uri();
        String desc = config.description();
        String auth = config.auth();

        if (uri == null && desc == null && auth == null) {
            helpAndFail("ERROR: Missing --uri, --description or --auth");
        }

        Optional<ManagedMasterDto> server = msr.getManagedServers(config.instanceGroup()).stream()
                .filter(m -> m.hostName.equals(config.update())).findFirst();
        if (server.isEmpty()) {
            throw new IllegalArgumentException("Cannot find server " + config.update());
        }

        DataResult result = createSuccess();

        ManagedMasterDto mmd = server.get();
        if (desc != null && !desc.isBlank()) {
            mmd.description = desc;
            result.addField("New Description", desc);
        }
        if (uri != null && !uri.isBlank()) {
            mmd.uri = uri;
            result.addField("New URI", uri);
        }
        if (auth != null && !auth.isBlank()) {
            result.addField("New Authentication", auth);
            mmd.auth = auth;
        }

        msr.updateManagedServer(config.instanceGroup(), config.update(), mmd);
        return result;
    }

    private DataResult attachManagedServer(CentralConfig config, ManagedServersResource msr) {
        helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");
        helpAndFailIfMissing(config.description(), "Missing --description");

        Path source = Paths.get(config.attach());
        ManagedMasterDto mmd;
        try (InputStream is = Files.newInputStream(source)) {
            mmd = StorageHelper.fromStream(is, ManagedMasterDto.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read managed master information from " + source);
        }

        mmd.description = config.description();
        if (config.name() != null) {
            mmd.hostName = config.name();
        }
        if (config.uri() != null) {
            mmd.uri = config.uri();
        }

        if (!config.offline()) {
            msr.tryAutoAttach(config.instanceGroup(), mmd);

            return createResultWithMessage("Managed Server has been automatically attached.")
                    .addField("Instance Group", config.instanceGroup()).addField("Managed Server", mmd.hostName);
        } else {
            helpAndFailIfMissing(config.output(), "Missing --output");

            String ident = msr.getCentralIdent(config.instanceGroup(), mmd);
            msr.manualAttach(config.instanceGroup(), mmd);

            Path target = Paths.get(config.output());
            try {
                Files.writeString(target, ident);
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot write central identification to " + target);
            }

            return createResultWithMessage("Server has been manually attached to the Instance Group " + config.instanceGroup())
                    .addField("Hint", "Please use the `remote-central --attachCentral` command with the file " + target
                            + " on the managed server now to complete offline attach.");
        }
    }

    private DataTable listManagedServers(CentralConfig config, RemoteService remote, ManagedServersResource msr) {
        helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");

        List<ManagedMasterDto> mmds = msr.getManagedServers(config.instanceGroup());

        DataTable table = createDataTable();
        table.setCaption("Managed servers for " + config.instanceGroup() + " on " + remote.getUri());

        table.column("Name", 20).column("URI", 40).column("Description", 40).column("Last Sync", 20);
        table.column(new DataTableColumn("NumberOfLocalMinions", "# Minions", 9));
        table.column(new DataTableColumn("NumberOfInstances", "# Inst.", 7));

        for (ManagedMasterDto mmd : mmds) {
            List<InstanceConfiguration> instances = msr.getInstancesControlledBy(config.instanceGroup(), mmd.hostName);

            table.row().cell(mmd.hostName).cell(mmd.uri).cell(mmd.description)
                    .cell(mmd.lastSync != null ? FormatHelper.format(mmd.lastSync) : "never").cell(mmd.minions.values().size())
                    .cell(instances.size()).build();
        }
        return table;
    }

    private DataResult getManagedServerIdent(CentralConfig config, BackendInfoResource bir, BackendInfoDto version) {
        checkMode(version, MinionMode.MANAGED);

        helpAndFailIfMissing(config.output(), "Missing --output");

        ManagedMasterDto mmd = bir.getManagedMasterIdentification();
        Path target = Paths.get(config.output());
        try (OutputStream os = Files.newOutputStream(target)) {
            os.write(StorageHelper.toRawBytes(mmd));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write to " + target, e);
        }

        return createSuccess().addField("Ident File", config.output());
    }

    private DataResult attachCentralServer(CentralConfig config, BackendInfoDto version, ManagedServersResource msr) {
        checkMode(version, MinionMode.MANAGED);

        Path source = Paths.get(config.attachCentral());
        String ident;
        try {
            ident = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read content of " + source);
        }

        String group = msr.manualAttachCentral(ident);
        return createSuccess().addField("Instance Group", group);
    }

    private void checkMode(BackendInfoDto version, MinionMode expected) {
        if (version.mode != expected) {
            throw new IllegalArgumentException("Target server has wrong mode for the requested command: " + version.mode);
        }
    }
}
