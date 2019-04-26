package io.bdeploy.ui.api.impl;

import org.glassfish.grizzly.http.server.CLStaticHttpHandler;

import io.bdeploy.jersey.RegistrationTarget;

public class UiResources {

    public static void register(RegistrationTarget server) {
        server.registerRoot(new CLStaticHttpHandler(UiResources.class.getClassLoader(), "/webapp/"));
        server.register(AuthResourceImpl.class);
        server.register(HiveResourceImpl.class);
        server.register(BackendInfoResourceImpl.class);

        server.register(InstanceGroupResourceImpl.class);
        server.register(SoftwareRepositoryResourceImpl.class);
    }

}
