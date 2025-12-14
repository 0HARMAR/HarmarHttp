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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Map;

import org.example.monitor.MonitorEndpoints;
import org.example.monitor.PerformanceMonitor;
import org.example.security.DosDefender;

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
    private final ConnectionManager connectionManager;

    public HarmarHttpServer(int port, String rootDir) throws IOException {
        this(port, rootDir,true, true);
    }

    public HarmarHttpServer(int port, String rootDir,boolean enableDosDefender, boolean enableMonitoring) throws IOException {
        this.port = port;
        this.rootDir = rootDir;
        // config 100 file and 100M limit
        this.fileCache = new FileCacheManager(100,Paths.get("src/main/resources/example"));

        this.dosDefender = enableDosDefender ?
                new DosDefender(60_000, 100, 300_000) : null;

        connectionManager = new ConnectionManager(this, port, 8);
        // init monitor
        this.enableMonitoring = enableMonitoring;
        if (enableMonitoring) {
            this.performanceMonitor = new PerformanceMonitor();
            this.monitorEndpoints = new MonitorEndpoints(performanceMonitor);
        } else {
            this.performanceMonitor = null;
            this.monitorEndpoints = null;
        }

        registerBuildInRoutes();
    }

    private void registerBuildInRoutes() {
        router.get("/api/time", ((request, response, pathParams) ->
                sendResponse(response.getByteArrayOutputStream(), HttpResponse.HttpStatus.OK.code, HttpResponse.HttpStatus.OK.message, "application/json",
                        ("{ \"serverTime\": \"" + new Date() + "\" }".getBytes()).getBytes())));

        router.get("/api/user/{id}", (request, response, pathParams) -> {
            String userId = pathParams.get("id");
            String json = "{ \"userId\": \"" + userId + "\", \"name\": \"User" + userId + "\" }";

            sendResponse(
                    response.getByteArrayOutputStream(),
                    HttpResponse.HttpStatus.OK.code,
                    HttpResponse.HttpStatus.OK.message,
                    "application/json",
                    json.getBytes()
            );
        });



        router.post("api/data", ((request, response, pathParams) -> {
            // TODO
            sendResponse(response.getByteArrayOutputStream(), HttpResponse.HttpStatus.OK.code, HttpResponse.HttpStatus.OK.message,
                    "application/json", "{ \"status\": \"OK\" }".getBytes());
        }));

        // register monitor endpoint
        if (enableMonitoring && monitorEndpoints != null) {
            monitorEndpoints.registerEndPoints(this);
        }
    }

    public void registerRoute(String method, String path, Router.RouteHandler handler) {
        router.register(method,path, handler);
    }

    public void start() throws IOException {
        if (isRunning) return;

        connectionManager.start();
        this.isRunning = true;

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

    private void handleRequest(Socket socket) {
        long startTime = System.currentTimeMillis();
        HttpRequest request = null;

        try (
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();
        ) {
            String clientIp = socket.getInetAddress().getHostAddress();

            // record request start
            if (enableMonitoring && performanceMonitor != null) {
                performanceMonitor.recordRequestStart();
            }

            // check dos defender
            if (dosDefender != null && !dosDefender.allowRequest(clientIp)) {
                sendResponse(output, HttpResponse.HttpStatus.TOO_MANY_REQUESTS.code,
                        HttpResponse.HttpStatus.TOO_MANY_REQUESTS.message, "text/html; charset=utf8", "You have exceeded the request limit".getBytes());
                return;
            }
            // get request
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            request = parseRequest(reader);

            // TODO
            if (request != null) {
                respondToRequest(output,request, reader);
            }

            // record request complete
            if (enableMonitoring && performanceMonitor != null) {
                long responseTime = System.currentTimeMillis() - startTime;
                // example status code is 200
                performanceMonitor.recordRequestComplete(responseTime, 200);
            }
        } catch (IOException e) {
            if (isRunning) {
                System.err.println("Request processing error: " + e.getMessage());
            }

            // record error
            if (enableMonitoring && performanceMonitor != null) {
                performanceMonitor.recordError(e.getClass().getSimpleName());
            }
        } finally {
            closeSocketQuietly(socket);
        }
    }

    private void closeSocketQuietly(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }
    }

    private HttpRequest parseRequest(BufferedReader reader) throws IOException {
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {return null;}

        String[] parts = requestLine.split("\\s+");
        if (parts.length < 2) {return null;}

        HttpRequest request = new HttpRequest();
        request.method = parts[0];
        request.path =  parts[1];
        request.protocol = parts.length > 2 ? parts[2] : "HTTP/1.0";

        request.headers = HttpHeaderParser.parseHeaders(reader);
        if ("POST".equalsIgnoreCase(request.method)) {
            request.hasBody = true;
        }
        return request;
    }

    private Response respondToRequest(OutputStream output, HttpRequest request, BufferedReader reader) throws IOException {
        // 1. try to match route
        Router.RouteMatch match = router.findMatch(request.method, request.path);
        Response response = new Response(output, false);
        if (match != null) {
            match.handler.handle(request, response, match.pathParams);
            return response;
        }

        // 2. if no route,execute default
        if ("GET".equalsIgnoreCase(request.method)) {
            handleGetRequest(output,request.path);
        } else if("POST".equalsIgnoreCase(request.method)) {
            PostRequestHandler postRequestHandler = new PostRequestHandler();
            postRequestHandler.handle(output, request, reader);
        } else if ("HEAD".equalsIgnoreCase(request.method)) {
            HeadRequestHandler headRequestHandler = new HeadRequestHandler();
            headRequestHandler.handle(output, request);
        }
        else  {
            sendResponse(output, HttpResponse.HttpStatus.NOT_IMPLEMENTED.code, HttpResponse.HttpStatus.NOT_IMPLEMENTED.message,
                    "text/html", ("Not Implemented" + request.method).getBytes());
        }

        return response;
    }

    private void handleGetRequest(OutputStream output, String path) throws IOException {
        if ("/".equals(path)) {
            path = "index.html";
        }

        // handle static file request
        serverStaticFile(output,path);
    }

    private void serverStaticFile(OutputStream output, String path) throws IOException {
        Path rootPath = Paths.get(this.rootDir).toAbsolutePath();
        Path requestPath = normalizePath(rootPath,path);

        // security auth
        if (!requestPath.startsWith(rootPath)) {
            sendResponse(output, HttpResponse.HttpStatus.FORBIDDEN.code, HttpResponse.HttpStatus.FORBIDDEN.message,
                    "text/html; charset=utf8", "403 FORBIDDEN".getBytes());
            return;
        }

        if (Files.exists(requestPath) && !Files.isDirectory(requestPath)) {
            String fileKey = requestPath.toString();

            // check cache
            FileCacheManager.CacheEntity cached = fileCache.get(fileKey);
            if (cached != null) {
                sendResponse(output, HttpResponse.HttpStatus.OK.code, HttpResponse.HttpStatus.OK.message,
                        cached.contentType, cached.content);
                return;
            }

            // cache not hit
            byte[] content = Files.readAllBytes(normalizePath(rootPath, String.valueOf(requestPath)));
            String contentType = determineContentType(requestPath);
            long lastModified = Files.getLastModifiedTime(requestPath).toMillis();

            // update cache
            String canonicalPath = requestPath.toRealPath().toString();
            fileCache.put(fileKey,
                    new FileCacheManager.CacheEntity(content,contentType,lastModified, canonicalPath));
            sendResponse(output, HttpResponse.HttpStatus.OK.code, HttpResponse.HttpStatus.OK.message, contentType, content);
        } else {
            sendResponse(output, HttpResponse.HttpStatus.NOT_FOUND.code, HttpResponse.HttpStatus.NOT_FOUND.message,
                    "text/html; charset=utf-8", buildErrorHtml(HttpResponse.HttpStatus.NOT_FOUND.code, HttpResponse.HttpStatus.NOT_FOUND.message));
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

    public static void sendResponse(OutputStream output, int statusCode, String statusMsg,
                                    String contentType, byte[] content, Map<String, String> headers) throws IOException {
        HttpResponse response = new HttpResponse(statusCode);
        response.setStatusMessage(statusMsg);

        response.setDefaultheaders();
        response.setHeader("Cache-Control", "no-cache");
        response.setContent(content, contentType);

        // add extra headers
        if (headers != null) {
            headers.forEach(response::setHeader);
        }

        response.send(output);
    }
    public static void sendResponse(OutputStream output, int statusCode, String statusMsg, String contentType, byte[] content) throws IOException {
        HttpResponse response = new HttpResponse(statusCode);
        response.setStatusMessage(statusMsg);

        response.setDefaultheaders();
        response.setHeader("Cache-Control", "no-cache");
        response.setContent(content, contentType);

        response.send(output);
    }

    public static void sendResponse(OutputStream output, int statusCode, String statusMsg) throws IOException {
        HttpResponse response = new HttpResponse(statusCode);
        response.setStatusMessage(statusMsg);

        response.setDefaultheaders();
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Transfer-Encoding", "chunked");

        response.send(output);
    }

    public static void sendHEADResponse(OutputStream output, int statusCode, String statusMsg, String contentType, String contentLength) throws IOException {
        HttpResponse response = new HttpResponse(statusCode);
        response.setStatusMessage(statusMsg);

        response.setDefaultheaders();
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Type", contentType);
        response.setHeader("Content-Length", String.valueOf(contentLength));

        response.send(output);
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

    public Response handleRawRequest(String rawRequest) {
        try {
            BufferedReader reader = new BufferedReader(new StringReader(rawRequest));
            HttpRequest request = parseRequest(reader);
            StringWriter sw = new StringWriter();
            ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
            Response response = respondToRequest(responseStream, request, null);;
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
