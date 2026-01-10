package org.example;

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
        performanceMonitor.recordRequestStart();
        ByteBuffer buffer = ByteBuffer.allocate(8192);

        readNextRequest(client, buffer);
    }

    private void readNextRequest(AsynchronousSocketChannel client, ByteBuffer buffer) {
        RequestMonitorContext context = new RequestMonitorContext();
        client.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {

            @Override
            public void completed(Integer bytesRead, ByteBuffer buf) {
                if (bytesRead == -1) {
                    close(client);
                    return;
                }

                buf.flip();
                byte[] data = new byte[buf.remaining()];
                buf.get(data);
                String requestText = new String(data);

                // ====== ✅ DosDefender 防御点 ======
                if (dosDefender != null) {
                    String ip = getClientIp(client);
                    if (!dosDefender.allowRequest(ip)) {
                        // ❌ 超限，直接返回 429
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
                        writeResponse(client, response, false, buffer, context);
                        return;
                    }
                }
                // ==================================

                // async process request
                    workerPool.submit(() -> {
                        boolean shouldKeepAlive = shouldKeepAlive(requestText);
                        HttpResponse response = server.handleRawRequest(requestText);
                        writeResponse(client, response, shouldKeepAlive, buffer, context);
                    });
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                exc.printStackTrace();
                close(client);
            }
        });
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

    public void writeResponse(AsynchronousSocketChannel client,
                              HttpResponse response,
                              boolean shouldKeepAlive,
                              ByteBuffer buffer,
                              RequestMonitorContext context) {
        String version = response.getHttpVersion().toString();
        String statusLine = version + " " + response.getStatus().code + " " + response.getStatus().message.toString() + "\r\n";
        Map<String, String> headers = response.getHeaders();
        String headersText = headers.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(java.util.stream.Collectors.joining("\r\n"));
        byte[] responseLineAndHeader = (statusLine + headersText + "\r\n\r\n").getBytes();
        ByteBuffer headerBuf = ByteBuffer.wrap(responseLineAndHeader);

        client.write(headerBuf, headerBuf, new CompletionHandler<>() {
            @Override
            public void completed(Integer result, ByteBuffer buf) {
                if (buf.hasRemaining()) {
                    client.write(buf, buf, this);
                } else {
                    // ✅ header write completely, write chunks
                    writeNextChunk(client, response, shouldKeepAlive, buffer, context);
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer buf) {
                System.err.println("❌ Header write failed: " + exc.getMessage());
                close(client);
            }
        });
    }

    public void writeNextChunk(AsynchronousSocketChannel client,
                              HttpResponse response,
                              boolean shouldKeepAlive,
                              ByteBuffer buffer, RequestMonitorContext context) {
        // write chunks
        ResponseBody body = response.getBody();
        ByteBuffer nextChunk = body.poll();

        if (nextChunk == null) {
            if (!body.isEnd()) {
                client.write(ByteBuffer.allocate(0), null, new CompletionHandler<Integer, Object>() {
                    @Override
                    public void completed(Integer result, Object attachment) {
                        writeNextChunk(client, response, shouldKeepAlive, buffer, context);
                    }

                    @Override
                    public void failed(Throwable exc, Object attachment) {
                        close(client);
                    }
                });
                return;
            }

            finishOrKeepAlive(client, shouldKeepAlive, buffer, context);
            return;
        }

        client.write(nextChunk, nextChunk, new CompletionHandler<Integer, ByteBuffer>() {

            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                if (attachment.hasRemaining()) {
                    client.write(attachment, attachment, this);
                } else {
                    writeNextChunk(client, response, shouldKeepAlive, buffer, context);
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("❌ Write failed: " + exc.getMessage());
                close(client);
            }
        });
    }

    private void finishOrKeepAlive(AsynchronousSocketChannel client, boolean shouldKeepAlive, ByteBuffer buffer, RequestMonitorContext context) {
        if (shouldKeepAlive) {
            buffer.clear();
            if (performanceMonitor != null) {
                long responseTime = System.currentTimeMillis() - context.getStartTime();
                performanceMonitor.recordRequestComplete(responseTime, 200);
            }
            readNextRequest(client, ByteBuffer.allocate(8192));
        } else {
            if (performanceMonitor != null) {
                long responseTime = System.currentTimeMillis() - context.getStartTime();
                performanceMonitor.recordRequestComplete(responseTime, 200);
            }
            close(client);
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

