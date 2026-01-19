package org.example;

import org.example.connection.AioConnection;
import org.example.connection.Connection;
import org.example.http2.Http2ConnectionManager;
import org.example.monitor.PerformanceMonitor;
import org.example.monitor.RequestMonitorContext;
import org.example.security.DosDefender;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


public class ConnectionManager {
    private final AsynchronousServerSocketChannel serverChannel;
    private final ExecutorService workerPool;
    private final HarmarHttpServer server;
    private PerformanceMonitor performanceMonitor = null;
    private DosDefender dosDefender = null;

    public ConnectionManager(HarmarHttpServer server, int port, int workerThreads,
                             PerformanceMonitor performanceMonitor, DosDefender dosDefender) throws IOException {
        this.server = server;
        this.workerPool = Executors.newFixedThreadPool(workerThreads);
        this.serverChannel = AsynchronousServerSocketChannel.open().bind(
                new InetSocketAddress(port)
        );
        this.performanceMonitor = performanceMonitor;
        this.dosDefender = dosDefender;
    }

    public void start() {
        System.out.println("⚡ [ConnectionManager] Listening on port " + getPort());

        acceptNext();
    }

    private void acceptNext() {
        serverChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel client, Void att) {
                // re listening next connection
                acceptNext();

                handleClient(client);
            }

            @Override
            public void failed(Throwable exc, Void att) {
                System.err.println("❌ Accept failed: " + exc.getMessage());
            }
        });
    }

    private void handleClient(AsynchronousSocketChannel client) {
        System.out.println("⚡ [ConnectionManager] New connection from " + getRemoteAddress(client));
        ConnectionContext ctx = new ConnectionContext(client);
        startRead(ctx);
    }

    private void startRead(ConnectionContext ctx) {
        ctx.client.read(ctx.buffer, ctx, new CompletionHandler<Integer, ConnectionContext>() {
            @Override
            public void completed(Integer bytesRead, ConnectionContext ctx) {
                if (bytesRead == -1) {
                    close(ctx.client);
                    return;
                }

                ctx.buffer.flip();

                // 协议探测阶段
                if (ctx.protocol == null) {
                    ctx.protocol = ProtocolDetector.detect(ctx.buffer);

                    if (ctx.protocol == null) {
                        ctx.buffer.compact();
                        ctx.client.read(ctx.buffer, ctx, this);
                        return;
                    }

                    if (ctx.protocol == Protocol.HTTP2) {
                        System.out.println("⚡ Detected HTTP/2 connection");
                        Connection connection = new AioConnection(ctx.client);
                        Http2ConnectionManager http2 = new Http2ConnectionManager(connection);
                        http2.start();
                        return; // HTTP/2 接管
                    }

                    System.out.println("⚡ Detected HTTP/1.x connection");
                }

                // HTTP/1.x 请求解析循环
                parseHttp1Requests(ctx, this);
            }

            @Override
            public void failed(Throwable exc, ConnectionContext ctx) {
                exc.printStackTrace();
                close(ctx.client);
            }
        });
    }


    private void parseHttp1Requests(ConnectionContext ctx, CompletionHandler<Integer, ConnectionContext> handler) {
        int requestEnd = findHttpRequestEnd(ctx.buffer);
        if (requestEnd == -1) {
            ctx.buffer.compact();
            ctx.client.read(ctx.buffer, ctx, handler);
            return;
        }

        byte[] data = new byte[requestEnd];
        ctx.buffer.get(data);
        String requestText = new String(data);

        boolean shouldKeepAlive = shouldKeepAlive(requestText);

        // DosDefender
        if (dosDefender != null && !dosDefender.allowRequest(getClientIp(ctx.client))) {
            HttpResponse response = new HttpResponse();
            response.setHttpVersion(justifyHttpVersion(requestText));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS);

            ResponseBody body = new ResponseBody();
            body.addChunk("Too Many Requests".getBytes());
            body.end();
            response.setBody(body);
            response.setDefaultHeaders();
            response.setHeader("Content-Type", "text/plain");
            response.setHeader("Content-Length", String.valueOf("Too Many Requests".getBytes().length));

            writeResponse(ctx, response, shouldKeepAlive, handler);
            return;
        }

        // 正常处理
        HttpResponse response = server.handleRawRequest(requestText);
        writeResponse(ctx, response, shouldKeepAlive, handler);
    }


    /**
     * 找到完整 HTTP/1 请求结束位置（按 \r\n\r\n 或 Content-Length）
     * 返回 -1 表示请求未完整
     */
    private int findHttpRequestEnd(ByteBuffer buf) {
        int startPos = buf.position();
        int limit = buf.limit();

        // 简单方式：找 \r\n\r\n
        for (int i = startPos; i < limit - 3; i++) {
            if (buf.get(i) == '\r' && buf.get(i + 1) == '\n'
                    && buf.get(i + 2) == '\r' && buf.get(i + 3) == '\n') {
                // 包含 header 长度
                int headersEnd = i + 4;

                // 检查是否有 Content-Length
                int contentLength = getContentLength(buf, startPos, headersEnd);
                if (contentLength > 0) {
                    // 确保 body 也完整
                    if (limit - headersEnd >= contentLength) {
                        return headersEnd + contentLength - startPos;
                    } else {
                        return -1; // body 未完整
                    }
                } else {
                    // 无 body，完整请求
                    return headersEnd - startPos;
                }
            }
        }

        return -1; // header 未完整
    }

    /**
     * 从 buffer 的 [start, end) 区间解析 Content-Length
     */
    private int getContentLength(ByteBuffer buf, int start, int end) {
        byte[] headerBytes = new byte[end - start];
        int oldPos = buf.position();
        buf.position(start);
        buf.get(headerBytes);
        buf.position(oldPos);

        String headers = new String(headerBytes);
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    return Integer.parseInt(line.split(":")[1].trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    private HttpVersion justifyHttpVersion(String requestText) {
        // get http version string
        String[] lines = requestText.split("\r\n");
        String httpVersion = lines[0].split(" ")[0];

        if (httpVersion.equals("HTTP/1.0")) {
            return HttpVersion.HTTP_1_0;
        } else {
            return HttpVersion.HTTP_1_1;
        }
    }

    private void writeResponse(ConnectionContext ctx, HttpResponse response,
                               boolean shouldKeepAlive,
                               CompletionHandler<Integer, ConnectionContext> handler) {
        if (shouldKeepAlive) {
            response.setHeader("Connection", "keep-alive");
        } else {
            response.setHeader("Connection", "close");
        }
        String version = response.getHttpVersion().toString();
        String statusLine = version + " " + response.getStatus().code + " " + response.getStatus().message + "\r\n";
        String headersText = response.getHeaders().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(java.util.stream.Collectors.joining("\r\n"));
        ByteBuffer headerBuf = ByteBuffer.wrap((statusLine + headersText + "\r\n\r\n").getBytes());

        ctx.client.write(headerBuf, ctx, new CompletionHandler<>() {
            @Override
            public void completed(Integer result, ConnectionContext ctx) {
                if (headerBuf.hasRemaining()) {
                    ctx.client.write(headerBuf, ctx, this);
                } else {
                    response.getBody().setOnDataAvailable(() -> {
                        tryWriteNextChunk(ctx, response, shouldKeepAlive, handler);
                    });

                    tryWriteNextChunk(ctx, response, shouldKeepAlive, handler);
                }
            }

            @Override
            public void failed(Throwable exc, ConnectionContext ctx) {
                exc.printStackTrace();
                close(ctx.client);
            }
        });
    }

    void tryWriteNextChunk(ConnectionContext ctx, HttpResponse response,
                           boolean shouldKeepAlive,
                           CompletionHandler<Integer, ConnectionContext> handler) {
        ResponseBody body = response.getBody();

        if (!body.trySetWriting()) {
            return; // 已有 write 在飞
        }

        ByteBuffer buf = body.poll();

        if (buf == null) {
            body.clearWriting();

            if (body.isEnd()) {
                finishOrKeepAlive(ctx, shouldKeepAlive, handler);
            }
            return;
        }
        writeNextChunk(ctx, response, shouldKeepAlive, handler, buf);
    }

    private void writeNextChunk(ConnectionContext ctx, HttpResponse response,
                                boolean shouldKeepAlive,
                                CompletionHandler<Integer, ConnectionContext> handler,
                                ByteBuffer chunk) {
        ctx.client.write(chunk, ctx, new CompletionHandler<>() {

            @Override
            public void completed(Integer result, ConnectionContext attachment) {
                if (result < 0) {
                    close(ctx.client);
                    return;
                }

                if (chunk.hasRemaining()) {
                    ctx.client.write(chunk, ctx, this);
                    return;
                }

                response.getBody().clearWriting();
                tryWriteNextChunk(ctx, response, shouldKeepAlive, handler);
            }

            @Override
            public void failed(Throwable exc, ConnectionContext attachment) {
                close(ctx.client);
            }
        });
    }
    private void finishOrKeepAlive(ConnectionContext ctx, boolean shouldKeepAlive,
                                   CompletionHandler<Integer, ConnectionContext> handler) {
        long responseTime = System.currentTimeMillis() - ctx.monitor.getStartTime();
        if (performanceMonitor != null) {
            performanceMonitor.recordRequestComplete(responseTime, 200);
        }

        if (shouldKeepAlive) {
            // 使用 compact 保留 buffer 中未读数据
            ctx.buffer.compact();
            // 只在响应完全写完后再触发 read
            startRead(ctx);
        } else {
            try { ctx.client.shutdownOutput(); } catch (IOException ignored) {}
            close(ctx.client);
        }
    }


    private void close(AsynchronousSocketChannel client) {
        try {
            System.out.println("⚡ [ConnectionManager] Closing connection from " + getRemoteAddress(client));
            client.close();
        } catch (IOException ignore) {
        }
    }

    private int getPort() {
        try {
            return ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
        } catch (IOException e) {
            return -1;
        }
    }

    public void shutdown() {
        workerPool.shutdown();
        try {
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
                System.out.println("⚡ [ConnectionManager] Server stopped");
            }
        } catch (IOException e) {
            System.err.println("❌ Error while closing server channel: " + e.getMessage());
        }
    }

    private boolean shouldKeepAlive(String requestText) {
       if (requestText == null) {
           return true;
       }

       String[] lines = requestText.split("\\r?\\n");
       for (String line : lines) {
           if (line.toLowerCase().startsWith("connection:")) {
               if (line.toLowerCase().contains("close")) {
                   return false;
               } else {
                   return true;
               }
           }
       }

       return true;
    }

    private String getRemoteAddress(AsynchronousSocketChannel client) {
        try {
            return client.getRemoteAddress().toString();
        } catch (IOException e) {
            return "Unknown";
        }
    }

    private String getClientIp(AsynchronousSocketChannel client) {
        try {
            InetSocketAddress addr = (InetSocketAddress) client.getRemoteAddress();
            return addr.getAddress().getHostAddress();
        } catch (IOException e) {
            return "unknown";
        }
    }

}

