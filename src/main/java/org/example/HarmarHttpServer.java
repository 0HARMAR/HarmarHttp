package org.example;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.Socket;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Map;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.example.http2.HpackDynamicTable;
import org.example.https.NettyTlsServer;
import org.example.monitor.MonitorEndpoints;
import org.example.monitor.PerformanceMonitor;
import org.example.security.DosDefender;

import static org.example.protocol.HttpRequestParser.parseRequest;

public class HarmarHttpServer {
    // monitor fields
    private final PerformanceMonitor performanceMonitor;
    private final MonitorEndpoints monitorEndpoints;
    private final boolean enableMonitoring;

    private int port = 80;
    private volatile boolean isRunning;
    public static String rootDir;
    private final FileCacheManager fileCache;
    private final DosDefender dosDefender;
    private final Router router = new Router();
    private final ConnectionManager connectionManager; // HTTP
    private final NettyTlsServer nettyTlsServer; // HTTPS

    public HarmarHttpServer(int port, String rootDir) throws IOException {
        this(port, rootDir,true, true, true);
    }

    public HarmarHttpServer(int port, String rootDir,boolean enableDosDefender, boolean enableMonitoring, boolean enableFileCache) throws IOException {
        this.port = port;
        this.rootDir = rootDir;
        // config 100 file and 100M limit
        this.fileCache = enableFileCache ? new FileCacheManager(Paths.get(rootDir)) : null;

        this.dosDefender = enableDosDefender ?
                new DosDefender(60_000, 100, 300_000) : null;

        // init monitor
        this.enableMonitoring = enableMonitoring;
        if (enableMonitoring) {
            this.performanceMonitor = new PerformanceMonitor();
            this.monitorEndpoints = new MonitorEndpoints(performanceMonitor);
        } else {
            this.performanceMonitor = null;
            this.monitorEndpoints = null;
        }

        // http default 80 port
        this.connectionManager = new ConnectionManager(this, 80, 10,
                enableDosDefender ? performanceMonitor : null,
                enableDosDefender ? dosDefender : null, router);

        this.nettyTlsServer = new NettyTlsServer(port,router);

        registerBuildInRoutes();
    }

    private void registerBuildInRoutes() {
        router.get("/api/time", (request, response, pathParams) -> {
            byte[] content = ("{ \"serverTime\": \"" + new Date() + "\" }")
                    .getBytes(StandardCharsets.UTF_8);

            ResponseBody body = new ResponseBody();
            body.addChunk(content);
            body.end();

            response.setStatus(HttpStatus.OK);
            response.setBody(body);
            response.setDefaultHeaders();
            response.setHeader("Content-Type", "application/json");
            response.setHeader("Content-Length", String.valueOf(content.length));

            response.send();
        });


        router.get("/api/user/{id}", (request, response, pathParams) -> {
            String userId = pathParams.get("id");
            byte[] content = (
                    "{ \"userId\": \"" + userId + "\", \"name\": \"User" + userId + "\" }"
            ).getBytes(StandardCharsets.UTF_8);

            ResponseBody body = new ResponseBody();
            body.addChunk(content);
            body.end();

            response.setStatus(HttpStatus.OK);
            response.setBody(body);
            response.setDefaultHeaders();
            response.setHeader("Content-Type", "application/json");
            response.setHeader("Content-Length", String.valueOf(content.length));

            response.send();
        });


        // register monitor endpoint
        if (enableMonitoring && monitorEndpoints != null) {
            monitorEndpoints.registerEndPoints(this);
        }
    }

    public void registerRouteHttp1(String method, String path, Router.Http1RouteHandler handler) {
        router.registerHttp1(method, path, handler);
    }

    public void registerRouteHttp2(String method, String path, Router.Http2RouteHandler handler) {
        router.registerHttp2(method, path, handler);
    }

    public void start() throws IOException {
        if (isRunning) return;

        connectionManager.start();
        this.isRunning = true;

        try {
            nettyTlsServer.start();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // validate is the rootDir exist
        validateRootDirectory();
    }

    private void validateRootDirectory() throws IOException {
        Path rootPath = Paths.get(this.rootDir).toAbsolutePath();
        if (!Files.exists(rootPath)) {
            throw new IOException(String.format("The root directory %s does not exist.", rootPath));
        }
        if (!Files.isDirectory(rootPath)) {
            throw new IOException(String.format("The root directory %s is not a directory.", rootPath));
        }
    }

    public void stop() {
        if (!isRunning) return;

        connectionManager.shutdown();

        isRunning = false;

        System.out.println("Server stopped");

        if (dosDefender != null) {
            dosDefender.shutdown();
        }
    }

    private HttpResponse respondToRequest(HttpRequest request) throws IOException {
        // 1. check http version
        HttpVersion version = request.protocol.getName().startsWith("HTTP/1.0") ? HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
        // 2. try to match route
        Router.RouteMatchHttp1 match = router.findMatchHttp1(request.method, request.path);

        HttpResponse response = new HttpResponse();
        response.setHttpVersion(version);

        if (match != null) {
            match.handler.handle(request, response, match.pathParams);
            return response;
        }

        // 3. if no route,execute default
        if ("GET".equalsIgnoreCase(request.method)) {
            handleGetRequest(response,request.path);
        }
        else  {
            byte[] content = ("Not Implemented " + request.method)
                    .getBytes(StandardCharsets.UTF_8);

            ResponseBody body = new ResponseBody();
            body.addChunk(content);
            body.end();

            response.setStatus(HttpStatus.NOT_IMPLEMENTED);
            response.setBody(body);

            response.setDefaultHeaders();
            response.setHeader("Content-Type", "text/html");
            response.setHeader("Content-Length", String.valueOf(content.length));

            response.send();
        }

        return response;
    }

    private void handleGetRequest(HttpResponse response, String path) throws IOException {
        if ("/".equals(path)) {
            path = "index.html";
        }

        // handle static file request
        serverStaticFile(response,path);
    }

    private void serverStaticFile(HttpResponse response, String path) throws IOException {
        Path rootPath = Paths.get(this.rootDir).toAbsolutePath();
        Path requestPath = normalizePath(rootPath,path);

        // security auth
        if (!requestPath.startsWith(rootPath)) {
            response.setStatus(HttpStatus.FORBIDDEN);

            ResponseBody body = new ResponseBody();
            byte[] content = "403 FORBIDDEN".getBytes();
            body.addChunk(content);
            body.end();
            response.setBody(body);

            response.setDefaultHeaders();
            response.setHeader("Content-Type", "text/html; charset=utf8");
            response.setHeader("Content-Length", String.valueOf(content.length));

            response.send();
            return;
        }

        if (Files.exists(requestPath) && !Files.isDirectory(requestPath)) {
                byte[] content = Files.readAllBytes(normalizePath(rootPath, String.valueOf(requestPath)));
                String contentType = determineContentType(requestPath);

                ResponseBody body = new ResponseBody();
                body.addChunk(content);
                body.end();
                response.setBody(body);
                response.setStatus(HttpStatus.OK);
                response.setDefaultHeaders();
                response.setHeader("Content-Type", contentType);
                response.setHeader("Content-Length", String.valueOf(content.length));
                response.send();
        } else {
            byte[] errorContent = buildErrorHtml(HttpStatus.NOT_FOUND.code, HttpStatus.NOT_FOUND.message);
            ResponseBody body = new ResponseBody();
            body.addChunk(errorContent);
            body.end();
            response.setBody(body);
            response.setStatus(HttpStatus.NOT_FOUND);
            response.setDefaultHeaders();
            response.setHeader("Content-Type", "text/html; charset=utf-8");
            response.setHeader("Content-Length", String.valueOf(errorContent.length));
            response.send();
        }
    }

    public static Path normalizePath(Path base, String path) {
        String sanitizedPath = path.replace('\\','/')
                .replace("..","");

        return base.resolve(sanitizedPath.startsWith("/") ?
                sanitizedPath.substring(1) : sanitizedPath)
                .normalize();
    }

    public static String determineContentType(Path filePath) {
        String fileName = filePath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex < 0) return "application/octet-stream";

        String extension = fileName.substring(dotIndex + 1).toLowerCase();

        switch (extension) {
            case "html": return  "text/html; charset=utf-8";
            case "css": return  "text/css";
            case "js": return  "application/javascript";
            case "json": return  "application/json";
            case "png": return  "image/png";
            case "jpg": case "jpeg": return  "image/jpeg";
            case "gif": return  "image/gif";
            case "ico": return  "image/x-icon";
            case "svg": return  "image/svg+xml";
            case "txt": return  "text/plain";
            default: return "application/octet-stream";
        }
    }

    private byte[] buildErrorHtml(int statusCode, String statusMsg) {
        return ("<!DOCTYPE html>\n" +
                        "<html lang=\"en\">\n" +
                        "<head>\n" +
                        "    <meta charset=\"UTF-8\">\n" +
                        "    <title>" + statusCode + " " + statusMsg + "</title>\n" +
                        "    <style>\n" +
                        "        body { font-family: Arial, sans-serif; background-color: #f8f8f8; text-align: center; padding: 50px; }\n" +
                        "        h1 { font-size: 48px; color: #cc0000; }\n" +
                        "        p { font-size: 20px; color: #333; }\n" +
                        "    </style>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "    <h1>" + statusCode + " " + statusMsg + "</h1>\n" +
                        "    <p>The server returned an error while processing your request.</p>\n" +
                        "</body>\n" +
                        "</html>").getBytes();
    }

    public HttpResponse handleRawRequest(String rawRequest) {
        try {
            BufferedReader reader = new BufferedReader(new StringReader(rawRequest));
            HttpRequest request = parseRequest(reader);
            HttpResponse response = respondToRequest(request);;
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
