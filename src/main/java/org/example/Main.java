package org.example;

public class Main {
    public static void main(String[] args) {
        try {
            HarmarHttpServer harmarHttpServer = new HarmarHttpServer(80,"src/main/resources/example",true);

            harmarHttpServer.registerRoute("GET", "/api/products/{category}",
                    ((request, output, pathParams) -> {
                        String category = pathParams.get("category");
                        String json = "{ \"category\": \"" + category + "\", \"products\": [...] }";
                        harmarHttpServer.sendJson(output, 200, "OK", json);
                    }));

            harmarHttpServer.registerRoute("POST", "/api/login",
                    ((request, output, pathParams) -> {
                        harmarHttpServer.sendJson(output, 200, "OK", "{ \"token\": \"abc\" }");
                    }));
            harmarHttpServer.start();

            Runtime.getRuntime().addShutdownHook(new Thread(harmarHttpServer::stop));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}