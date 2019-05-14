/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.internal.variables.StringVariableManager;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.di.extensions.Service;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tea.core.BackgroundTask;
import org.eclipse.tea.core.TaskExecutionContext;
import org.eclipse.tea.core.annotations.TaskChainContextInit;
import org.eclipse.tea.core.annotations.TaskChainMenuEntry;
import org.eclipse.tea.core.services.TaskChain;
import org.eclipse.tea.core.services.TaskChain.TaskChainId;
import org.eclipse.tea.core.services.TaskingLog;
import org.eclipse.tea.core.ui.annotations.TaskChainUiInit;
import org.eclipse.tea.library.build.config.BuildDirectories;
import org.eclipse.tea.library.build.jar.ZipExecFactory;
import org.eclipse.tea.library.build.lcdsl.tasks.p2.DynamicProductBuildRegistry;
import org.eclipse.tea.library.build.menu.BuildLibraryMenu;
import org.eclipse.tea.library.build.tasks.jar.TaskInitJarCache;
import org.osgi.service.component.annotations.Component;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.tea.plugin.BDeployBuildProductTask.ProductDesc;
import io.bdeploy.tea.plugin.services.BDeployApplicationBuild;
import io.bdeploy.tea.plugin.services.BDeployApplicationDescriptor;
import io.bdeploy.tea.plugin.services.BDeployApplicationService;
import io.bdeploy.tea.plugin.services.BDeployApplicationService.CreateApplicationTasks;

@SuppressWarnings("restriction")
@Component
@TaskChainId(description = "Build BDeploy Product", alias = "BDeployProduct")
@TaskChainMenuEntry(path = BuildLibraryMenu.MENU_BUILD, groupingId = "BDeploy", icon = "icons/bdeploy.png")
public class BDeployProductTaskChain implements TaskChain {

    private Path bdeployProductFile;

    @TaskChainUiInit
    public void uiInit(Shell parent, BDeployConfig cfg) throws IOException, CoreException {
        bdeployProductFile = null;

        if (cfg.bdeployProductListFile == null || cfg.bdeployProductListFile.isEmpty()) {
            throw new IllegalArgumentException("No BDeploy Product List File set in configuration");
        }

        String listFile = StringVariableManager.getDefault().performStringSubstitution(cfg.bdeployProductListFile);
        BDeployProductListDescriptor listDesc;
        Path listPath = Paths.get(listFile);
        try (InputStream is = Files.newInputStream(listPath)) {
            listDesc = StorageHelper.fromYamlStream(is, BDeployProductListDescriptor.class);
        }

        if (listDesc == null || listDesc.products.isEmpty()) {
            throw new IllegalStateException("Cannot find any product to build in the workspace");
        }

        Path rootPath = listPath.getParent();

        if (listDesc.products.size() == 1) {
            bdeployProductFile = rootPath.resolve(listDesc.products.values().iterator().next());
        } else {
            BDeployChooseProductFileDialog dlg = new BDeployChooseProductFileDialog(parent, listDesc);
            dlg.setBlockOnOpen(true);
            if (dlg.open() != Dialog.OK) {
                throw new OperationCanceledException();
            }

            bdeployProductFile = rootPath.resolve(dlg.getChosenFile());
        }
    }

    @SuppressWarnings("unchecked")
    @TaskChainContextInit
    public void init(TaskExecutionContext c, TaskingLog log, BDeployConfig cfg, DynamicProductBuildRegistry registry,
            BuildDirectories dirs, @Service List<BDeployApplicationService> appServices, IEclipseContext ctx)
            throws CoreException {

        // to produce reproducible JAR files (timestamps).
        ZipExecFactory.setIgnoreExternalZipExe(true);

        c.addTask(BDeployCheckServerOnlineTask.class);

        TaskInitJarCache cache = new TaskInitJarCache(dirs.getNewCacheDirectory("jar"));
        c.addTask(cache);

        if (bdeployProductFile == null) {
            bdeployProductFile = Paths.get(cfg.bdeployProductFile);
        }

        if (cfg == null || bdeployProductFile == null) {
            throw new IllegalStateException("BDeploy is not configured, set configuration");
        }

        if (!Files.exists(bdeployProductFile)) {
            throw new IllegalStateException("Configuration file does not exist: " + bdeployProductFile);
        }

        BDeployProductDescriptor desc;
        try (InputStream is = Files.newInputStream(bdeployProductFile)) {
            desc = StorageHelper.fromYamlStream(is, BDeployProductDescriptor.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read configuration in " + bdeployProductFile, e);
        }

        ProductDesc pd = new ProductDesc();
        pd.productInfo = bdeployProductFile.getParent().resolve(desc.productInfoYaml);

        for (BDeployApplicationDescriptor app : desc.applications) {
            Optional<BDeployApplicationService> handler = appServices.stream().filter(s -> s.canHandle(app.type)).findFirst();
            if (handler.isPresent()) {
                IEclipseContext child = ctx.createChild("Create BDeploy Application " + app.name);
                child.set(BDeployApplicationDescriptor.class, app);

                Object o = ContextInjectionFactory.invoke(handler.get(), CreateApplicationTasks.class, child);
                if (!(o instanceof List)) {
                    throw new IllegalStateException("Service " + handler.get() + " did not return a list of "
                            + BDeployApplicationBuild.class.getSimpleName());
                }

                pd.apps.addAll((List<BDeployApplicationBuild>) o);

                child.dispose();
            } else {
                throw new IllegalArgumentException("Unknown application type: " + app.type);
            }
        }

        c.addTask(BackgroundTask
                .allBarrier(pd.apps.stream().map(a -> a.task).filter(Objects::nonNull).collect(Collectors.toList())));

        BDeployBuildProductTask build = new BDeployBuildProductTask(pd);
        c.addTask(build);
        c.addTask(new BDeployPackageProductTask(build));
        c.addTask(cache.getCleanup());
    }

}
