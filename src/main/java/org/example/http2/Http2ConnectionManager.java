package org.example.http2;

import org.example.Router;
import org.example.connection.AioConnection;
import org.example.connection.Connection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class Http2ConnectionManager {
    private final AioConnection client;
    private Router router;
    Http2Manager http2Manager = new Http2Manager(router);

    public Http2ConnectionManager(AioConnection client, Router router) {
        this.client = client;
        this.router = router;
    }

    public void start() {
        readNextFrame();
    }

    private void readNextFrame() {
        client.onRead(readBuffer -> {
            boolean hasFrame = http2Manager.decodeAndHandle(readBuffer);
            if (hasFrame) {
                // combine control frame and stream response to
                // a ByteBuffer
                BlockingQueue<ByteBuffer> controlFrameQueue = http2Manager.getControlFrameQueue();
                Map<Integer, Http2Stream> streams = http2Manager.getStreams();
                BlockingQueue<Frame> responseQueues = new LinkedBlockingQueue<>();
                for (Http2Stream stream : streams.values()) {
                    BlockingQueue<Frame> responseQueue = stream.getResponseQueue();
                    responseQueues.addAll(responseQueue);
                }
                client.getControlFrameQueue().addAll(controlFrameQueue);
                while (!responseQueues.isEmpty()) {
                    client.getStreamsQueue().add(ByteBuffer.wrap(responseQueues.poll().toBytes()));
                }
                client.write();
            }
        });
    }


    private void close() {
        client.close();
        System.out.println("⚡ HTTP/2 connection closed");
    }
}
