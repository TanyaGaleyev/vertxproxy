package org.ivan.proxy;

import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.net.impl.TrustAllTrustManager;
import org.junit.Test;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class VertxProxyTest {

    @Test
    public void proxy() throws Exception {
        Vertx proxyContext = Vertx.vertx();
        deployVerticleSynchronously(new VertxProxy(9090, 9091), proxyContext);

        CompletableFuture<Void> serverStarted = new CompletableFuture<>();
        Vertx.vertx().createHttpServer().requestHandler(request -> {
            long[] requestBytes = {0};
            request.handler(b -> {
                for (int i = 0; i < b.length(); i++) {
                    b.getByte(i);
                    requestBytes[0]++;
                }
            });
            request.endHandler(v -> {
                request.response().end("Request processed (" + requestBytes[0] + " bytes)");
            });
        }).listen(9091, ar -> {
            if (ar.succeeded()) {
                serverStarted.complete(null);
            } else {
                serverStarted.completeExceptionally(ar.cause());
            }
        });
        serverStarted.join();
        byte[] payload = Stream.generate(() -> "A")
                .limit(100000)
                .reduce("", (s1, s2) -> s1 + s2)
                .getBytes("US-ASCII");
        sendRequests(payload);
    }

    private void sendRequests(byte[] payload) throws Exception {
        for (int i = 0; i < 1000; i++) {
            URL url = new URL("https://localhost:9090");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(1024);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{TrustAllTrustManager.INSTANCE}, null);
            connection.setSSLSocketFactory(sslContext.getSocketFactory());
            connection.setHostnameVerifier((s, sslSession) -> true);
            byte[] buf = new byte[1024];
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
                try (InputStream in = connection.getInputStream()) {
                    while ((in.read(buf)) != -1) ;
                }
            }
        }
    }

    private void deployVerticleSynchronously(Verticle v, Vertx vertx) {
        CompletableFuture<Void> verticleStarted = new CompletableFuture<>();
        vertx.deployVerticle(new VertxProxy(9090, 9091), ar -> {
            if (ar.succeeded()) {
                verticleStarted.complete(null);
            } else {
                verticleStarted.completeExceptionally(ar.cause());
            }
        });
        verticleStarted.join();
    }
}