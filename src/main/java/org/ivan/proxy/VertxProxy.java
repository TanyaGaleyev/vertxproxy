package org.ivan.proxy;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;

public class VertxProxy extends AbstractVerticle {

    private final int listenPort;
    private final int destinationPort;

    public VertxProxy(int listenPort, int destinationPort) {
        this.listenPort = listenPort;
        this.destinationPort = destinationPort;
    }

    @Override
    public void start(final Future<Void> startFuture) throws Exception {
        HttpClient client = vertx.createHttpClient(new HttpClientOptions());
        JksOptions jksOptions = new JksOptions();
        jksOptions.setPath("keystore.jks");
        jksOptions.setPassword("changeit");
        HttpServerOptions serverOptions = new HttpServerOptions()
                .setSsl(true)
                .setKeyStoreOptions(jksOptions);
        vertx.createHttpServer(serverOptions).requestHandler(req -> {
//            System.out.println("Proxying request: " + req.uri());
            HttpClientRequest c_req = client.request(req.method(), destinationPort, "localhost", req.uri(), c_res -> {
//                System.out.println("Proxying response: " + c_res.statusCode());
                req.response().setChunked(true);
                req.response().setStatusCode(c_res.statusCode());
                req.response().headers().setAll(c_res.headers());
                c_res.handler(data -> {
//                    System.out.println("Proxying response body: " + data.toString("ISO-8859-1"));
                    req.response().write(data);
                });
                c_res.endHandler((v) -> req.response().end());
            });
            c_req.setChunked(true);
            c_req.headers().setAll(req.headers());
            req.handler(data -> {
//                System.out.println("Proxying request body " + data.toString("ISO-8859-1"));
//                System.out.println("Proxying request body (" + data.length() + " bytes)");
                c_req.write(data);
            });
            req.endHandler((v) -> c_req.end());
        }).listen(listenPort, ar -> {
            if (ar.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(ar.cause());
            }
        });
    }
}