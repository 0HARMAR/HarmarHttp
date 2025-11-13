package org.example;

public class Main {
    public static void main(String[] args) {
        try {
            HarmarHttpServer harmarHttpServer = new HarmarHttpServer(80,"src/main/resources/example");

            harmarHttpServer.registerRoute("GET", "/api/products/{category}",
                    ((request, output, pathParams) -> {
                        String category = pathParams.get("category");
                        String json = "{ \"category\": \"" + category + "\", \"products\": [...] }";
                        harmarHttpServer.sendResponse(output, HttpResponse.HttpStatus.OK.code,
                                HttpResponse.HttpStatus.OK.message, "application/json", json.getBytes());
                    }));

            harmarHttpServer.registerRoute("POST", "/api/login",
                    ((request, output, pathParams) -> {
                        harmarHttpServer.sendResponse(output, HttpResponse.HttpStatus.OK.code,
                                HttpResponse.HttpStatus.OK.message, "application/json", "{ \"token\": \"abc\" }".getBytes());
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