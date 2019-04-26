package io.bdeploy.bhive.remote.jersey;

import java.util.SortedMap;
import java.util.SortedSet;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.jersey.JerseyAuthenticationProvider.WeakTokenAllowed;

/**
 * Resource allowing access to a single {@link BHive}.
 */
@Path("/hive")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface BHiveResource {

    /**
     * Retrieve all {@link Key}s along with the root tree {@link ObjectId} available
     * to the remote repository.
     *
     * @param names
     */
    @POST
    @WeakTokenAllowed
    @Path("/manifests")
    public SortedMap<Manifest.Key, ObjectId> getManifestInventory(String... names);

    /**
     * @param key the manifest to delete.
     */
    @POST
    @Path("/manifest_remove")
    public void removeManifest(Manifest.Key key);

    /**
     * Perform a prune operation on the remote {@link BHive}, removing any unreferenced objects.
     */
    @POST
    @Path("/prune")
    public void prune();

    /**
     * From the given set, filter all remotely known {@link ObjectId}s and return only {@link ObjectId} which are not yet present
     * on the remote.
     */
    @POST
    @WeakTokenAllowed
    @Path("/obj_missing")
    public SortedSet<ObjectId> getMissingObjects(SortedSet<ObjectId> all);

    /**
     * Retrieve the {@link ObjectId} required to satisfy a given tree.
     */
    @POST
    @WeakTokenAllowed
    @Path("/tree_objects")
    public SortedSet<ObjectId> getRequiredObjects(ObjectListSpec spec);

    /**
     * Retrieve the {@link ObjectId}s of all required {@link Tree} objects recursively in the given tree.
     */
    @POST
    @WeakTokenAllowed
    @Path("/tree_trees")
    public SortedSet<ObjectId> getRequiredTrees(ObjectId tree);

    /**
     * Transfer the ZIPed {@link BHive} to the remote and apply all top-level
     * {@link Manifest}s referenced within.
     */
    @PUT
    @Path("/push")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void push(java.nio.file.Path zipedHive);

    /**
     * Fetch manifests from the remote as ZIPed {@link BHive}. Objects with an
     * {@link ObjectId} contained in the availableObjects {@link SortedSet} are not
     * included.
     * <p>
     * The caller is responsible for cleaning up the file pointed at by the returned
     * {@link Path}.
     */
    @POST
    @WeakTokenAllowed
    @Path("/fetch")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public java.nio.file.Path fetch(FetchSpec spec);

    public static class FetchSpec {

        SortedSet<ObjectId> requiredObjects;
        SortedSet<Manifest.Key> manifestsToFetch;
    }

    public static class ObjectListSpec {

        SortedSet<ObjectId> trees;
        SortedSet<ObjectId> excludeTrees;
    }

}
