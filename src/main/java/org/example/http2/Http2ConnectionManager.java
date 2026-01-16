package org.example.http2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Http2ConnectionManager {
    private final AsynchronousSocketChannel client;
    private final Map<Integer, Http2Stream> streams = new ConcurrentHashMap<>();
    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    private HpackDynamicTable hpackDynamicTable = new HpackDynamicTable(4096);
    private final FrameDecoder decoder = new FrameDecoder();

    public Http2ConnectionManager(AsynchronousSocketChannel client) {
        this.client = client;
    }

    public void start() {
        readNextFrame();
    }

    private void readNextFrame() {
        client.read(readBuffer, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                if (result == -1) {
                    close();
                    return;
                }

                readBuffer.flip();

                // 解码所有完整帧
                List<Frame> frames = decoder.decode(readBuffer);

                readBuffer.clear();

                for (Frame frame : frames) {
                    System.out.println("⚡ Received frame: " + frame);

                    switch (frame.header.FrameType) {
                        case SETTINGS -> handleSettings(frame);

                        case HEADERS -> {
                            Http2Stream stream = streams.computeIfAbsent(frame.header.StreamID, Http2Stream::new);
                            stream.onRecvFrame(frame);
                            handleHeaders(frame);
                        }
                        case DATA, RST_STREAM -> {
                            // 找已有 stream 或新建
                            Http2Stream stream = streams.computeIfAbsent(frame.header.StreamID, Http2Stream::new);

                            // 推入请求帧，并更新状态
                            stream.onRecvFrame(frame);

                        }

                        default -> {
                            System.out.println("⚠️ Unknown frame type: " + frame.header.FrameType);
                        }
                    }
                }

                // 继续异步读取
                readNextFrame();
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                exc.printStackTrace();
                close();
            }
        });
    }


    private void handleHeaders(Frame frame) {
        int streamId = frame.header.StreamID;

        Http2Stream stream = streams.computeIfAbsent(streamId, Http2Stream::new);

        // unpack HPACK
        Map<String, String> headers = new ConcurrentHashMap<>();
        byte[] hpack = frame.getPayload();
        try {
            HpackDecoder decoder = new HpackDecoder(hpackDynamicTable);
            headers = decoder.decode(hpack);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            System.out.println("  " + key + ": " + value);
        }

        // 构造响应帧
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        responseHeaders.put(":status", "200");
        responseHeaders.put("content-type", "text/plain");
        responseHeaders.put("content-length", "5");
        responseHeaders.put("server", "mini-http2");
        responseHeaders.put("user", "bob");

        byte[] headersPayload = new HpackEncoder(hpackDynamicTable).encode(responseHeaders);

        Frame headersFrame = new Frame(new FrameHeader(
                headersPayload.length, FrameType.HEADERS, EnumSet.of(FrameFlag.END_HEADERS), streamId
        ), headersPayload);

        byte[] dataPayload = "hello".getBytes(StandardCharsets.UTF_8);
        Frame dataFrame = new Frame(new FrameHeader(
                dataPayload.length, FrameType.DATA, EnumSet.of(FrameFlag.END_STREAM), streamId
        ), dataPayload);

        // 放入响应队列，由 Stream 状态机控制顺序
        stream.queueResponse(headersFrame);
        stream.queueResponse(dataFrame);

        // 启动写
        writeNext(stream);
    }

    /**
     *  * +-----------------------------------------------+
     *  * | Length (24) = 0                               |
     *  * | Type (8)    = 0x4 (SETTINGS)                  |
     *  * | Flags (8)   = 0x1 (ACK)                       |
     *  * | Stream ID (31) = 0                             |
     *  * +-----------------------------------------------+
     *  * | Payload = none (长度为0)                        |
     */
    private void handleSettings(Frame frame) {
        // ACK = 1, client ACK, ignore
        if ((frame.header.FrameFlags.contains(FrameFlag.ACK))) {
            return;
        }

        // ACK = 0, apply settings and send ACK to client
        Frame ack = new Frame(new FrameHeader(0, FrameType.SETTINGS, EnumSet.of(FrameFlag.ACK), 0), null);
        ByteBuffer buf = ByteBuffer.allocate(9); // fix 9 bytes frame header, no payload
        buf.put(ack.toBytes());
        buf.flip();
        client.write(buf, null, new CompletionHandler<Integer, Void>() {

            @Override
            public void completed(Integer result, Void attachment) {
                if (result == -1) {
                    close();
                    return;
                }
                else if(buf.hasRemaining()) {
                    client.write(buf, null, this);
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {

            }
        });
    }

    private void writeNext(Http2Stream stream) {
        Frame frame = stream.getResponseQueue().poll();
        if (frame == null) return; // 队列空，等下一次 push

        ByteBuffer buf = ByteBuffer.wrap(frame.toBytes());
        client.write(buf, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                if (result == -1) { close(); return; }
                if (buf.hasRemaining()) {
                    client.write(buf, null, this);
                } else {
                    // 写完当前帧，继续写队列里下一帧
                    writeNext(stream);
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                exc.printStackTrace();
                close();
            }
        });
    }


    private void close() {
        try { client.close(); } catch (IOException ignore) {}
        System.out.println("⚡ HTTP/2 connection closed");
    }
}
