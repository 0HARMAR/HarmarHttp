package org.example.http2;

import org.example.HttpRequest;
import org.example.Protocol;
import org.example.Router;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class Http2Manager {
    private BlockingQueue<ByteBuffer> controlFrameQueue = new LinkedBlockingQueue<>();
    private final Map<Integer, Http2Stream> streams = new ConcurrentHashMap<>();
    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    private HpackDynamicTable hpackDynamicTable = new HpackDynamicTable(4096);
    private final FrameDecoder decoder = new FrameDecoder();
    private final Router router;

    public Http2Manager(Router router) {
        // 默认设置
        Frame settings = new Frame(new FrameHeader(0, FrameType.SETTINGS, null, 0), null);
        ByteBuffer buf = ByteBuffer.allocate(9); // fix 9 bytes frame header, no payload
        buf.put(settings.toBytes());
        buf.flip();
        try {
            controlFrameQueue.put(buf);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        this.router = router;
    }

    public boolean decodeAndHandle(ByteBuffer readBuffer) {
        // 解码所有完整帧
        List<Frame> frames = decoder.decode(readBuffer);
        if (frames.isEmpty()) {
            return false;
        }

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
                case WINDOW_UPDATE, GOAWAY -> {

                }

                default -> {
                    System.out.println("⚠️ Unknown frame type: " + frame.header.FrameType);
                }
            }
        }
        return true;
    }

    private void handleHeaders(Frame frame) {
        int streamId = frame.header.StreamID;

        Http2Stream stream = streams.computeIfAbsent(streamId, Http2Stream::new);

        // unpack HPACK
        Map<String, String> headers = new ConcurrentHashMap<>();
        byte[] hpack = frame.getPayload();
        try {
            HpackDecoder decoder = new HpackDecoder(hpackDynamicTable);
            headers = decoder.decode(frame);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            System.out.println("  " + key + ": " + value);
        }

        HttpRequest request = new HttpRequest();
        request.method = headers.get(":method");
        request.path = headers.get(":path");
        request.protocol = Protocol.HTTP2_OVER_TLS;

        Router.RouteMatchHttp2 match = router.findMatchHttp2(headers.get(":method"), headers.get(":path"));
        if (match != null) {
            try {
                match.handler.handle(request, stream.getResponseQueue(), match.pathParams, hpackDynamicTable, streamId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     *  * +-----------------------------------------------+
     *  * | Length (24) = 0                               |
     *  * | Type (8)    = 0x4 (SETTINGS)                  |
     *  * | Flags (8)                                     |
     *  * | Stream ID (31) = 0                             |
     *  * +-----------------------------------------------+
     *  * | Payload                                        |
     */
    private void handleSettings(Frame frame) {
        // ACK = 1, client ACK, ignore
        if ((frame.header.FrameFlags.contains(FrameFlag.ACK))) {
            return;
        }

        // parse config and apply
        byte[] settingsConfig = frame.payload;
        EnumSet<SettingsConfig> configs = parseConfig(settingsConfig);
        for (SettingsConfig config : configs) {
            System.out.println("  " + config.name() + ": " + config.getDefaultValue());
        }

        // ACK = 0, apply settings and send ACK to client
        Frame ack = new Frame(new FrameHeader(0, FrameType.SETTINGS, EnumSet.of(FrameFlag.ACK), 0), null);
        ByteBuffer buf = ByteBuffer.allocate(9); // fix 9 bytes frame header, no payload
        buf.put(ack.toBytes());
        buf.flip();
        try {
            controlFrameQueue.put(buf);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private EnumSet<SettingsConfig> parseConfig(byte[] payload) {
        EnumSet<SettingsConfig> result = EnumSet.noneOf(SettingsConfig.class);

        if (payload == null || payload.length % 6 != 0) {
            throw new IllegalArgumentException("Invalid SETTINGS payload length");
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        while (buffer.hasRemaining()) {
            // 2 bytes ID (unsigned short)
            int id = Short.toUnsignedInt(buffer.getShort());
            // 4 bytes value (unsigned int)
            int value = buffer.getInt();

            SettingsConfig config = SettingsConfig.fromId(id);
            if (config != null) {
                // 创建一个新的枚举实例并存储 value
                // 因为 enum 本身不能存值，我们可以用一个临时封装类或者 Map 记录
                // 这里为了简单，直接用 EnumSet 返回“存在的配置项”
                result.add(config);

                // 你也可以在这里直接应用 value，比如：
                // applySetting(config, value);
            } else {
                // 未知设置项，RFC 建议忽略
                System.out.println("Unknown SETTINGS ID: " + id);
            }
        }

        return result;
    }

    public BlockingQueue<ByteBuffer> getControlFrameQueue() {
        return controlFrameQueue;
    }

    public Map<Integer, Http2Stream> getStreams() {
        return this.streams;
    }
}
