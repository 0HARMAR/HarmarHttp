package org.example;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class Main {
    public static void main(String[] args) {
        try {
            HarmarHttpServer harmarHttpServer = new HarmarHttpServer(80,"src/main/resources/example");

            harmarHttpServer.start();

            harmarHttpServer.registerRoute("GET", "/date", (request, response, pathParams) -> {
                response.setStatus(HttpStatus.OK);
                response.setDefaultHeaders();
                byte[] content = ("{ \"serverTime\": \"" + new Date() + "\" }")
                        .getBytes(StandardCharsets.UTF_8);
                response.setHeader("Content-Type", "text/html; charset=utf-8");
                response.setHeader("Content-Length", String.valueOf(content.length));
                ResponseBody body = new ResponseBody();
                body.addChunk(content);
                body.end();
                response.setBody(body);
            });

            ResponseBody chunkedBody = new ResponseBody();
            harmarHttpServer.registerRoute("GET", "/api/chunk", (request, response, pathParams) -> {
                response.setStatus(HttpStatus.OK);
                response.setDefaultHeaders();
                response.setHeader("Content-Type", "text/html; charset=utf-8");
                response.setHeader("Transfer-Encoding", "chunked");
                response.setBody(chunkedBody);
                return;
            });

            chunkedBody.addChunk("Hello".getBytes(StandardCharsets.UTF_8));
            chunkedBody.addChunk(" World".getBytes(StandardCharsets.UTF_8));
            chunkedBody.end();

            System.out.println("Server started with monitoring enabled!");
            System.out.println("Monitor endpoints:");
            System.out.println("  - Health check: http://localhost:80/health");
            System.out.println("  - Metrics: http://localhost:80/metrics");
            System.out.println("  - Stats dashboard: http://localhost:80/stats");
            System.out.println("  - Reset stats: POST http://localhost:80/reset");

            Runtime.getRuntime().addShutdownHook(new Thread(harmarHttpServer::stop));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}