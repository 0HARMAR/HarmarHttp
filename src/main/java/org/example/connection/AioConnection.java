package org.example.connection;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

public class AioConnection implements Connection{
    private final AsynchronousSocketChannel client;
    private ReadHandler readHandler;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);

    public AioConnection(AsynchronousSocketChannel client) {
        this.client = client;
    }

    @Override
    public void write(ByteBuffer buffer) {
        client.write(buffer, null, new CompletionHandler<>() {

            @Override
            public void completed(Integer result, Object attachment) {

            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                close();
            }
        });
    }

    @Override
    public void onRead(ReadHandler handler) {
        this.readHandler = handler;
        readLoop();
    }

    private void readLoop() {
        readBuffer.clear();
        client.read(readBuffer, null, new CompletionHandler<>() {
            @Override
            public void completed(Integer result, Object attachment) {
                if (result == -1) {
                    close();
                    return;
                }
                readBuffer.flip();
                readHandler.onRead(readBuffer);
                readLoop();
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                close();
            }
        });
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (Exception ignored){}
    }
}
