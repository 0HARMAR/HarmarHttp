package org.example;

import org.example.http2.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        try {
            HarmarHttpServer harmarHttpServer = new HarmarHttpServer(8443,"src/main/resources/example", false, true, true);

            harmarHttpServer.registerRouteHttp1("GET", "/date", (request, response, pathParams) -> {
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

            harmarHttpServer.registerRouteHttp1("GET", "/api/hello", (request, response, pathParams) -> {
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

            harmarHttpServer.registerRouteHttp2("GET", "/api/http2", (request, stream, pathParams,
                                                                      hpackDynamicTable, streamId) -> {
                Map<String, String> responseHeaders = new LinkedHashMap<>();
                responseHeaders.put(":status", "200");
                responseHeaders.put("server", "mini-http2");

                byte[] headersPayload = new HpackEncoder(hpackDynamicTable).encode(responseHeaders);

                Frame headersFrame = new Frame(new FrameHeader(
                        headersPayload.length, FrameType.HEADERS, EnumSet.of(FrameFlag.END_HEADERS), streamId
                ), headersPayload);

                Frame dataFrame = new Frame(new FrameHeader(
                        "Hello, World!".getBytes(StandardCharsets.UTF_8).length, FrameType.DATA, EnumSet.of(FrameFlag.END_STREAM), streamId
                ), "Hello, World!".getBytes(StandardCharsets.UTF_8));

                stream.queueResponse(headersFrame);
                stream.queueResponse(dataFrame);
            });

            harmarHttpServer.registerHttp2StaticFile("/nijika.jpg");
            harmarHttpServer.registerHttp2StaticFile("/test_pic1.jpg");
            harmarHttpServer.registerHttp2StaticFile("/test_pic2.jpg");
            harmarHttpServer.registerHttp2StaticFile("/test_pic3.jpg");
            harmarHttpServer.registerHttp2StaticFile("/test_pic4.jpg");
            harmarHttpServer.registerHttp2StaticFile("/big_file.zip");

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
            harmarHttpServer.registerRouteHttp1("GET", "/api/chunk", (request, response, pathParams) -> {
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