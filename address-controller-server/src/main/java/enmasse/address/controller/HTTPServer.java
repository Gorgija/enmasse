package enmasse.address.controller;

import enmasse.address.controller.admin.AddressManager;
import enmasse.address.controller.restapi.v1.RestService;
import io.vertx.core.AbstractVerticle;
import org.jboss.resteasy.plugins.server.vertx.VertxRequestHandler;
import org.jboss.resteasy.plugins.server.vertx.VertxResteasyDeployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP server for deploying address config
 */
public class HTTPServer extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(HTTPServer.class.getName());
    private final AddressManager addressManager;

    public HTTPServer(AddressManager addressManager) {
        this.addressManager = addressManager;
    }

    @Override
    public void start() {
        VertxResteasyDeployment deployment = new VertxResteasyDeployment();
        deployment.start();
        deployment.getRegistry().addSingletonResource(new RestService(addressManager));

        vertx.createHttpServer()
                .requestHandler(new VertxRequestHandler(vertx, deployment))
                .listen(8080, ar -> log.info("Started HTTP server"));
    }
}
