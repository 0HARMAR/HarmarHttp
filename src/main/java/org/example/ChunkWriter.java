package org.example;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class ChunkWriter {
    private final AsynchronousSocketChannel client;

    public ChunkWriter(AsynchronousSocketChannel client) {
        this.client = client;
    }

    /*
     * write a chunk
     */
    public CompletableFuture<Void> writeChunk(byte[] data) {
        String header = Integer.toHexString(data.length) + "\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
        byte[] ending = "\r\n".getBytes(StandardCharsets.US_ASCII);

        ByteBuffer buf = ByteBuffer.allocate(
                headerBytes.length + data.length + ending.length
        );
        buf.put(headerBytes);
        buf.put(data);
        buf.put(ending);
        buf.flip();

        return writeAsync(buf);
    }

    /*
     * write ending signal
     */
    public CompletableFuture<Void> finish() {
        ByteBuffer end = ByteBuffer.wrap("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        return writeAsync(end);
    }

    /*
     * write to the client asynchronously
     */
    private CompletableFuture<Void> writeAsync(ByteBuffer buf) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        client.write(buf, null, new CompletionHandler<Integer, Void>() {

            @Override
            public void completed(Integer result, Void attachment) {
                if (buf.hasRemaining()) {
                    client.write(buf, null, this);
                } else {
                    future.complete(null);
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                future.completeExceptionally(exc);
            }
        });
        return future;
    }
}
