package org.example;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class Main {
    public static void main(String[] args) {
        try {
            HarmarHttpServer harmarHttpServer = new HarmarHttpServer(8443,"src/main/resources/example");

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

            harmarHttpServer.registerRoute("GET", "/api/hello", (request, response, pathParams) -> {
                response.setStatus(HttpStatus.OK);
                response.setDefaultHeaders();
                byte[] content = "Hello, HTTPS".getBytes(StandardCharsets.UTF_8);
                response.setHeader("Content-Type", "text/html; charset=utf-8");
                response.setHeader("Content-Length", String.valueOf(content.length));
                ResponseBody body = new ResponseBody();
                body.addChunk(content);
                body.end();
                response.setBody(body);
            });

            ResponseBody chunkedBody = new ResponseBody();
            // 创建定时任务
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    chunkedBody.addChunk("World".getBytes(StandardCharsets.UTF_8));
                    chunkedBody.end();
                }
            };
            Timer timer = new Timer();
            harmarHttpServer.registerRoute("GET", "/api/chunk", (request, response, pathParams) -> {
                response.setStatus(HttpStatus.OK);
                response.setDefaultHeaders();
                response.setHeader("Content-Type", "text/html; charset=utf-8");
                response.setHeader("Transfer-Encoding", "chunked");
                chunkedBody.setChunkedTransfer(true);
                chunkedBody.addChunk("Hello".getBytes(StandardCharsets.UTF_8));
                response.setBody(chunkedBody);
                timer.schedule(task, 5000);
            });

            System.out.println("Server started with monitoring enabled!");
            System.out.println("Monitor endpoints:");
            System.out.println("  - Health check: http://localhost:80/health");
            System.out.println("  - Metrics: http://localhost:80/metrics");
            System.out.println("  - Stats dashboard: http://localhost:80/stats");
            System.out.println("  - Reset stats: POST http://localhost:80/reset");

            harmarHttpServer.start();

            Runtime.getRuntime().addShutdownHook(new Thread(harmarHttpServer::stop));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}