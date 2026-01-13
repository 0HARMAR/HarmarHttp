package org.example.http2;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class FrameDecoder {
    // HTTP/2 max frame payload is 16kb, change through SETTINGS
    private static final int DEFAULT_BUFFER_SIZE = 64 * 1024;

    /**
     * Accumulation buffer (connection-level)
     */
    private final ByteBuffer cumulation;

    public FrameDecoder() {
        this(DEFAULT_BUFFER_SIZE);
    }

    public FrameDecoder(int bufferSize) {
        this.cumulation = ByteBuffer.allocate(bufferSize);
    }

    /**
     * parse read data to 0..N Frames
     */
    public List<Frame> decode(ByteBuffer in) {
        List<Frame> frames = new ArrayList<>();

        // 1. cumulate new data
        append(in);

        // 2. try cut out frames
        while (true) {
            // HTTP/2 frame header fix 9 bytes
            if (cumulation.remaining() < 9) {
                break;
            }

            cumulation.mark();

            // 3. parse header
            FrameHeader header = FrameHeader.parse(cumulation);
            int payloadLength = header.FrameLength;

            // 4. payload is not complete, rollback
            if (cumulation.remaining() < payloadLength) {
                cumulation.reset();
                break;
            }

            // 5. read payload
            byte[] payload = new byte[payloadLength];
            cumulation.get(payload);

            frames.add(new Frame(header, payload));
        }

        // 6. ready for next read
        cumulation.compact();

        return frames;
    }

    private void append(ByteBuffer in) {
        cumulation.put(in);
        cumulation.flip();
    }
}
