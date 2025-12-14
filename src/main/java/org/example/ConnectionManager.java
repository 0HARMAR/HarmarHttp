package org.example;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


public class ConnectionManager {
    private final AsynchronousServerSocketChannel serverChannel;
    private final ExecutorService workerPool;
    private final HarmarHttpServer server;

    public ConnectionManager(HarmarHttpServer server, int port, int workerThreads) throws IOException {
        this.server = server;
        this.workerPool = Executors.newFixedThreadPool(workerThreads);
        this.serverChannel = AsynchronousServerSocketChannel.open().bind(
                new InetSocketAddress(port)
        );
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
        ByteBuffer buffer = ByteBuffer.allocate(8192);

        readNextRequest(client, buffer);
    }

    private void readNextRequest(AsynchronousSocketChannel client, ByteBuffer buffer) {
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

                // async process request
                    workerPool.submit(() -> {
                        boolean shouldKeepAlive = shouldKeepAlive(requestText);
                        Response response = server.handleRawRequest(requestText);
                        if (response.getChunkedTransfer())
                        {
                            writeResponse(client, response, shouldKeepAlive, buffer);
                        }
                        else {
                            byte[] responseData = response.getByteArrayOutputStream().toByteArray();
                            writeResponse(client, responseData, shouldKeepAlive, buffer);
                        }
                    });
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                exc.printStackTrace();
                close(client);
            }
        });
    }

    public void writeResponse(AsynchronousSocketChannel client,
                              Response response,
                              boolean shouldKeepAlive,
                              ByteBuffer buffer) {
        ByteBuffer headerBuf = ByteBuffer.wrap(response.getResponseLineAndHeader());

        client.write(headerBuf, headerBuf, new CompletionHandler<>() {
            @Override
            public void completed(Integer result, ByteBuffer buf) {
                if (buf.hasRemaining()) {
                    client.write(buf, buf, this);
                } else {
                    // ✅ header write completely, write chunks
                    writeNextChunk(client, response, shouldKeepAlive, buffer);
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
                              Response response,
                              boolean shouldKeepAlive,
                              ByteBuffer buffer) {
        // write chunks
        ByteBuffer nextChunk = response.poll();

        if (nextChunk == null) {
            if (!response.isEnd()) {
                client.write(ByteBuffer.allocate(0), null, new CompletionHandler<Integer, Object>() {
                    @Override
                    public void completed(Integer result, Object attachment) {
                        writeNextChunk(client, response, shouldKeepAlive, buffer);
                    }

                    @Override
                    public void failed(Throwable exc, Object attachment) {
                        close(client);
                    }
                });
                return;
            }

            finishOrKeepAlive(client, shouldKeepAlive, buffer);
            return;
        }

        client.write(nextChunk, nextChunk, new CompletionHandler<Integer, ByteBuffer>() {

            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                if (attachment.hasRemaining()) {
                    client.write(attachment, attachment, this);
                } else {
                    writeNextChunk(client, response, shouldKeepAlive, buffer);
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("❌ Write failed: " + exc.getMessage());
                close(client);
            }
        });
    }

    public void writeResponse(AsynchronousSocketChannel client,
                              byte[] data,
                              boolean shouldKeepAlive,
                              ByteBuffer buffer) {
        ByteBuffer responseBuffer = ByteBuffer.wrap(data);

        client.write(responseBuffer, responseBuffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                if (attachment.hasRemaining()) {
                    client.write(attachment, attachment, this);
                } else {
                    if (shouldKeepAlive) {
                        buffer.clear();
                        readNextRequest(client, ByteBuffer.allocate(8192));
                    } else {
                        close(client);
                    }
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("❌ Write failed: " + exc.getMessage());
                close(client);
            }
        });
    }

    private void finishOrKeepAlive(AsynchronousSocketChannel client, boolean shouldKeepAlive, ByteBuffer buffer) {
        if (shouldKeepAlive) {
            buffer.clear();
            readNextRequest(client, ByteBuffer.allocate(8192));
        } else {
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
}

