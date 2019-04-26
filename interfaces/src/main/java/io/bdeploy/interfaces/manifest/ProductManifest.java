package io.bdeploy.interfaces.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.InsertManifestRefOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestRefScanOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.op.TreeLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.product.ProductDescriptor;
import io.bdeploy.interfaces.descriptor.product.ProductVersionDescriptor;
import io.bdeploy.interfaces.manifest.dependencies.DependencyFetcher;

/**
 * A special manifestation of a {@link Manifest} which must follow a certain layout and groups multiple applications together
 * which are deployed via an 'instance' to for a version-consistent bundle.
 */
public class ProductManifest {

    public static final String PRODUCT_LABEL = "X-Product";
    public static final String PRODUCT_DESC = "product.json";

    private final SortedSet<Manifest.Key> applications;
    private final SortedSet<Manifest.Key> references;
    private final String prodName;
    private final Key key;
    private final ProductDescriptor desc;

    private ProductManifest(String name, Manifest.Key key, SortedSet<Manifest.Key> applications,
            SortedSet<Manifest.Key> references, ProductDescriptor desc) {
        this.prodName = name;
        this.key = key;
        this.applications = applications;
        this.references = references;
        this.desc = desc;
    }

    /**
     * @param hive the {@link BHive} to read from
     * @param manifest the {@link Manifest} which represents the {@link ProductManifest}
     * @return a {@link ProductManifest} or <code>null</code> if the given {@link Manifest} is not a {@link ProductManifest}.
     */
    public static ProductManifest of(BHive hive, Manifest.Key manifest) {
        Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(manifest));
        String label = mf.getLabels().get(PRODUCT_LABEL);
        if (label == null) {
            return null;
        }

        SortedSet<Key> allRefs = new TreeSet<>(
                hive.execute(new ManifestRefScanOperation().setManifest(manifest).setMaxDepth(2)).values());

        SortedSet<Key> appRefs = new TreeSet<>();
        SortedSet<Key> otherRefs = new TreeSet<>();

        for (Manifest.Key ref : allRefs) {
            try {
                ApplicationManifest.of(hive, ref);
                appRefs.add(ref);
            } catch (Exception e) {
                // not an application
                otherRefs.add(ref);
            }
        }

        Tree tree = hive.execute(new TreeLoadOperation().setTree(mf.getRoot()));
        ObjectId djId = tree.getNamedEntry(PRODUCT_DESC).getValue();

        ProductDescriptor desc;
        try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(djId))) {
            desc = StorageHelper.fromStream(is, ProductDescriptor.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load deployment manifest", e);
        }

        return new ProductManifest(label, manifest, appRefs, otherRefs, desc);
    }

    /**
     * @return the name of the product.
     */
    public String getProduct() {
        return prodName;
    }

    /**
     * @return the product's descriptor.
     */
    public ProductDescriptor getProductDescriptor() {
        return desc;
    }

    /**
     * @return the product {@link Manifest} {@link Key}.
     */
    public Key getKey() {
        return key;
    }

    /**
     * @return the applications grouped by this product
     */
    public SortedSet<Manifest.Key> getApplications() {
        return applications;
    }

    /**
     * @return additionally referenced non-application manifests (e.g. dependencies of applications).
     */
    public SortedSet<Manifest.Key> getReferences() {
        return references;
    }

    /**
     * @param descriptorPath a relative or absolute path to a directory or a product-info.yaml file.
     * @param hive the target hive to import to.
     * @param fetcher a {@link DependencyFetcher} capable of assuring that external dependencies are present.
     */
    public static Manifest.Key importFromDescriptor(String descriptorPath, BHive hive, DependencyFetcher fetcher) {
        // 1. read product desc yaml.
        Path impDesc = Paths.get(descriptorPath);
        if (Files.isDirectory(impDesc)) {
            impDesc = impDesc.resolve("product-info.yaml");
        }
        impDesc = impDesc.toAbsolutePath();
        ProductDescriptor prod = readProductDescriptor(impDesc);

        // 2. read version descriptor.
        if (prod.versionFile == null || prod.versionFile.isEmpty()) {
            throw new IllegalStateException(
                    "product descriptor does not reference a product version descriptor, which is required.");
        }
        // path is relative to product descriptor, or absolute.
        Path vDesc = impDesc.getParent().resolve(prod.versionFile);
        ProductVersionDescriptor versions = readProductVersionDescriptor(impDesc, vDesc);

        // 3. validate product and version info.
        RuntimeAssert.assertNotNull(versions.version, "no version defined in " + vDesc);
        RuntimeAssert.assertNotNull(prod.name, "no name defined in " + impDesc);
        RuntimeAssert.assertNotNull(prod.product, "no name defined in " + impDesc);

        // make sure that all applications are there in the version descriptor.
        Map<String, Map<OperatingSystem, String>> toImport = new TreeMap<>();
        matchApplicationsWithDescriptor(prod, vDesc, versions, toImport);

        // 4. prepare product meta-data and builder to be filled.
        String baseName = prod.product + '/';
        Manifest.Key prodKey = new Manifest.Key(baseName + "product", versions.version);
        Builder builder = new Builder(prod);

        // 5. find and import all applications to import.
        Path impBasePath = impDesc.getParent();
        importApplications(hive, fetcher, versions, toImport, baseName, builder, impBasePath);

        // 6. additional labels
        versions.labels.forEach(builder::addLabel);

        // 7. generate product
        builder.insert(hive, prodKey, prod.product);

        return prodKey;
    }

    private static void matchApplicationsWithDescriptor(ProductDescriptor prod, Path vDesc, ProductVersionDescriptor versions,
            Map<String, Map<OperatingSystem, String>> toImport) {
        for (String appName : prod.applications) {
            Map<OperatingSystem, String> map = versions.appInfo.get(appName);
            if (map == null || map.isEmpty()) {
                throw new IllegalStateException("Cannot find build information for " + appName + " in " + vDesc);
            }

            // sanity check - the product manifest will be called 'product'
            RuntimeAssert.assertFalse("product".equals(appName), "application may not be named 'product'");

            toImport.put(appName, map);
        }
    }

    private static void importApplications(BHive hive, DependencyFetcher fetcher, ProductVersionDescriptor versions,
            Map<String, Map<OperatingSystem, String>> toImport, String baseName, Builder builder, Path impBasePath) {
        for (Map.Entry<String, Map<OperatingSystem, String>> entry : toImport.entrySet()) {
            for (Map.Entry<OperatingSystem, String> relApp : entry.getValue().entrySet()) {
                Path appPath = impBasePath.resolve(relApp.getValue());
                if (Files.isDirectory(appPath)) {
                    appPath = appPath.resolve(ApplicationDescriptor.FILE_NAME);
                }

                if (!Files.exists(appPath)) {
                    throw new IllegalStateException("Cannot find " + appPath + " while importing " + entry.getKey());
                }

                // read and resolve dependencies.
                ApplicationDescriptor appDesc;
                try (InputStream is = Files.newInputStream(appPath)) {
                    appDesc = StorageHelper.fromYamlStream(is, ApplicationDescriptor.class);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read " + appPath);
                }

                RuntimeAssert.assertTrue(appDesc.supportedOperatingSystems.contains(relApp.getKey()),
                        "Application " + entry.getKey() + " does not support operating system " + relApp.getKey());

                fetcher.fetch(hive, appDesc.runtimeDependencies, relApp.getKey()).forEach(builder::add);

                builder.add(hive.execute(new ImportOperation().setSourcePath(appPath.getParent()).setManifest(
                        new ScopedManifestKey(baseName + entry.getKey(), relApp.getKey(), versions.version).getKey())));
            }
        }
    }

    private static ProductVersionDescriptor readProductVersionDescriptor(Path impDesc, Path vDesc) {
        if (!Files.exists(vDesc)) {
            throw new IllegalStateException("Cannot find version descriptor at " + vDesc);
        }
        ProductVersionDescriptor versions;
        try (InputStream is = Files.newInputStream(vDesc)) {
            versions = StorageHelper.fromYamlStream(is, ProductVersionDescriptor.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + impDesc, e);
        }
        return versions;
    }

    private static ProductDescriptor readProductDescriptor(Path impDesc) {
        if (!Files.exists(impDesc)) {
            throw new IllegalArgumentException("Product descriptor does not exist: " + impDesc);
        }
        ProductDescriptor prod;
        try (InputStream is = Files.newInputStream(impDesc)) {
            prod = StorageHelper.fromYamlStream(is, ProductDescriptor.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + impDesc, e);
        }
        return prod;
    }

    /**
     * @param hive the {@link BHive} to scan for available {@link ProductManifest}s.
     * @return a {@link SortedSet} with all available {@link ProductManifest}s.
     */
    public static SortedSet<Manifest.Key> scan(BHive hive) {
        SortedSet<Manifest.Key> result = new TreeSet<>();
        SortedSet<Manifest.Key> allKeys = hive.execute(new ManifestListOperation());
        for (Manifest.Key key : allKeys) {
            Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));
            if (mf.getLabels().containsKey(PRODUCT_LABEL)) {
                result.add(key);
            }
        }
        return result;
    }

    /**
     * Builder to create new product manifests.
     */
    public static class Builder {

        private final Map<String, Manifest.Key> applications = new TreeMap<>();
        private final Map<String, String> labels = new TreeMap<>();
        private final ProductDescriptor desc;

        public Builder(ProductDescriptor desc) {
            this.desc = desc;
        }

        public Builder add(Manifest.Key application) {
            applications.put(application.directoryFriendlyName(), application);
            return this;
        }

        public Builder addLabel(String key, String value) {
            labels.put(key, value);
            return this;
        }

        public void insert(BHive hive, Manifest.Key manifest, String productName) {
            Tree.Builder tree = new Tree.Builder();
            applications.forEach((k, v) -> tree.add(new Tree.Key(k, Tree.EntryType.MANIFEST),
                    hive.execute(new InsertManifestRefOperation().setManifest(v))));

            ObjectId descId = hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(desc)));
            tree.add(new Tree.Key(PRODUCT_DESC, Tree.EntryType.BLOB), descId);

            Manifest.Builder m = new Manifest.Builder(manifest);
            labels.forEach(m::addLabel);
            m.addLabel(PRODUCT_LABEL, productName).setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(tree)));
            hive.execute(new InsertManifestOperation().addManifest(m.build()));
        }
    }

}
