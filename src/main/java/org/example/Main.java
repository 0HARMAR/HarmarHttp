package org.example;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

public class Main {
    public static void main(String[] args) {
        try {
            HarmarHttpServer harmarHttpServer = new HarmarHttpServer(80,"src/main/resources/example");

            harmarHttpServer.registerRoute("GET", "/api/products/{category}",
                    ((request, response, pathParams) -> {
                        String category = pathParams.get("category");
                        String json = "{ \"category\": \"" + category + "\", \"products\": [...] }";
                        harmarHttpServer.sendResponse(response.getByteArrayOutputStream(), HttpResponse.HttpStatus.OK.code,
                                HttpResponse.HttpStatus.OK.message, "application/json", json.getBytes());
                    }));

            harmarHttpServer.registerRoute("POST", "/api/login",
                    ((request, response, pathParams) -> {
                        harmarHttpServer.sendResponse(response.getByteArrayOutputStream(), HttpResponse.HttpStatus.OK.code,
                                HttpResponse.HttpStatus.OK.message, "application/json", "{ \"token\": \"abc\" }".getBytes());
                    }));

            harmarHttpServer.registerRoute("GET", "/api/chunk",
                    ((request, response, pathParams) -> {
                        ByteArrayOutputStream responseLineAndHeader = new ByteArrayOutputStream();
                        harmarHttpServer.sendResponse(responseLineAndHeader, HttpResponse.HttpStatus.OK.code,
                                HttpResponse.HttpStatus.OK.message);
                        byte[] responseLineAndHeaderBytes = responseLineAndHeader.toByteArray();
                        response.setResponseLineAndHeader(responseLineAndHeaderBytes);
                        response.setChunkedTransfer();
                        response.addChunk("Hello\r\n".getBytes());
                        response.addChunk("Chunk1\r\n".getBytes());
                        // delay some time
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        response.addChunk("Chunk2\r\n".getBytes());
                        response.addChunk("\r\n\r\n".getBytes());
                        response.end();
                    }));
            harmarHttpServer.start();

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