package org.example;

import javax.xml.crypto.Data;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HarmarHttpServer {
    private final int port;
    private final String rootDir;
    private final ExecutorService threadPool;
    private ServerSocket serverSocket;
    private volatile boolean isRunning;
    private final FileCacheManager fileCache;
    private final DosDefender dosDefender;
    private final Router router = new Router();

    public HarmarHttpServer(int port, String rootDir) {
        this(port, rootDir,true);
    }

    public HarmarHttpServer(int port, String rootDir,boolean enableDosDefender) {
        this.port = port;
        this.rootDir = rootDir;
        this.threadPool = Executors.newFixedThreadPool(10);
        // config 100 file and 100M limit
        this.fileCache = new FileCacheManager(100,10 * 1024 * 1024);

        this.dosDefender = enableDosDefender ?
                new DosDefender(60_000, 100, 300_000) : null;
        registerBuildInRoutes();
    }

    private void registerBuildInRoutes() {
        router.get("/api/time", ((request, output, pathParams) ->
                sendJson(output, 200, "OK", "{ \"serverTime\": \"" + new Date() + "\" }")));

        router.get("/api/user/{id}", ((request, output, pathParams) -> {
            String userId = pathParams.get("id");
            String json = "{ \"userId\": \"" + userId + "\", \"name\": \"User" + userId +"\" }";
        }));

        router.post("api/data", ((request, output, pathParams) -> {
            // TODO
            sendJson(output, 200, "Created", "{ \"status\": \"OK\" }");
        }));
    }

    public void registerRoute(String method, String path, Router.RouteHandler handler) {
        router.register(method,path, handler);
    }

    public void start() throws IOException {
        if (isRunning) return;

        this.serverSocket = new ServerSocket(port);
        this.isRunning = true;

        // validate is the rootDir exist
        validateRootDirectory();

        new Thread(this::acceptConnections,"Server-Acceptor").start();
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
    private void acceptConnections() {
        System.out.println("Server started at port: " + this.port + "Serving " + this.rootDir);

        try {
            while (isRunning) {
                Socket clientSocker = serverSocket.accept();
                threadPool.execute(() -> handleRequest(clientSocker));
            }
        } catch (SocketException e) {
            if (isRunning) {
                System.err.println("Socket error: " + e.getMessage());
            }
        } catch (IOException e) {
            if (isRunning) {
                System.err.println("IO error: " + e.getMessage());
            }
        }
    }

    public void stop() {
        if (!isRunning) return;

        isRunning = false;
        threadPool.shutdown();

        try {
            threadPool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        closeSocketQuietly();
        System.out.println("Server stopped");

        if (dosDefender != null) {
            dosDefender.shutdown();
        }
    }

    private void closeSocketQuietly() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }
    }

    private void handleRequest(Socket socket) {
        try (
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();
        ) {
            String clientIp = socket.getInetAddress().getHostAddress();

            // check dos defender
            if (dosDefender != null && !dosDefender.allowRequest(clientIp)) {
                sendError(output,429,"Too Many Request",
                        "You have exceeded the request limit");
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            HttpRequest request = parseRequest(reader);

            if (request != null) {
                respondToRequest(output,request, reader);
            }
        } catch (IOException e) {
            if (isRunning) {
                System.err.println("Request processing error: " + e.getMessage());
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

    private void respondToRequest(OutputStream output, HttpRequest request, BufferedReader reader) throws IOException {
        // 1. try to match route
        Router.RouteMatch match = router.findMatch(request.method, request.path);
        if (match != null) {
            match.handler.handle(request, output, match.pathParams);
            return;
        }

        // 2. if no route,execute default
        if ("GET".equalsIgnoreCase(request.method)) {
            handleGetRequest(output,request.path);
        } else if("POST".equalsIgnoreCase(request.method)) {
            PostRequestHandler postRequestHandler = new PostRequestHandler();
            postRequestHandler.handle(output, request, reader);
        } else  {
            sendError(output,501,"Not Implemented","Unsupported method: " + request.method);
        }
    }

    private void handleGetRequest(OutputStream output, String path) throws IOException{
        // api endpoint process
        if ("/api/time".equals(path)) {
            sendJson(output,200,"OK","{ \"serverTime\": \"" + new Date() + "\" }");
            return;
        }

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
            sendError(output,403,"Forbidden","access to this resource is not allow");
            return;
        }

        if (Files.exists(requestPath) && !Files.isDirectory(requestPath)) {
            String fileKey = requestPath.toString();

            // check cache
            FileCacheManager.CacheEntity cached = fileCache.get(fileKey);
            if (cached != null) {
                sendResponse(output,200,"OK",cached.contentType,cached.content);
                return;
            }

            // cache not hit
            byte[] content = Files.readAllBytes(requestPath);
            String contentType = determineContentType(requestPath);
            long lastModified = Files.getLastModifiedTime(requestPath).toMillis();

            // update cache
            fileCache.put(fileKey,
                    new FileCacheManager.CacheEntity(content,contentType,lastModified));
            sendResponse(output,200,"OK",contentType,content);
        } else {
            sendError(output,404,"Not Found","Resource not found" + path);
        }
    }

    private Path normalizePath(Path base, String path) {
        String sanitizedPath = path.replace('\\','/')
                .replace("..","");

        return base.resolve(sanitizedPath.startsWith("/") ?
                sanitizedPath.substring(1) : sanitizedPath)
                .normalize();
    }

    private String determineContentType(Path filePath) {
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

    public static void sendJson(OutputStream output, int statusCode, String statusMsg, String json) throws IOException {
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        sendResponse(output,statusCode,statusMsg,"application/json",content);
    }

    public static void sendError(OutputStream output, int statusCode, String statusMsg, String message) throws IOException{
        String html = String.format(
                "<DOCTYPE html><html><head><title>%d %s</title><head>" +
                "<body><h1>%d %s<h1><p>%s</p></body></html>",
                statusCode,statusMsg,statusCode,statusMsg,message
        );

        sendResponse(output,statusCode,statusMsg,"text/html; charset=UTF-8",html.getBytes(StandardCharsets.UTF_8));
    }

    public static void sendResponse(OutputStream output, int statusCode, String statusMsg, String contentType, byte[] content) throws IOException {
        PrintWriter writer = new PrintWriter(output);

        // response header
        writer.printf("HTTP/1.1 %d %s\n", statusCode, statusMsg);
        writer.printf("Content-Type: %s\n", contentType);
        writer.printf("Content-Length: %d\n", content.length);
        writer.printf("Cache-Control: no-cache\n");
        writer.printf("Server: Harmar Http Server\n");
        writer.printf("Date: %s\n", new Date());
        writer.printf("Connection: close\n");
        writer.printf("%n");
        writer.flush();

        // response body
        output.write(content);
        output.flush();
    }
}
