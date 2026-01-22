package org.example.connection;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AioConnection implements Connection{
    private final AsynchronousSocketChannel client;
    private ReadHandler readHandler;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    BlockingQueue<ByteBuffer> controlFrameQueue = new LinkedBlockingQueue<>();
    BlockingQueue<ByteBuffer> streamsQueue = new LinkedBlockingQueue<>();
    private AtomicBoolean writting = new AtomicBoolean(false);

    public AioConnection(AsynchronousSocketChannel client) {
        this.client = client;
    }

    @Override
    public void write() {
        if (!writting.compareAndSet(false, true)) {
            return;
        }
        doWrite();
    }

    private void doWrite() {
        ByteBuffer buffer = controlFrameQueue.poll();
        if (buffer == null) {
            buffer = streamsQueue.poll();
        }

        if (buffer == null) {
            writting.set(false);
            return;
        }

        client.write(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer buf) {
                if (buf.hasRemaining()) {
                    client.write(buf, null, this);
                } else {
                    doWrite(); // 🚀 写下一个
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
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

    public BlockingQueue<ByteBuffer> getControlFrameQueue() {
        return controlFrameQueue;
    }

    public BlockingQueue<ByteBuffer> getStreamsQueue() {
        return streamsQueue;
    }
}
