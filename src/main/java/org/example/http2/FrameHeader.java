package org.example.http2;

import java.nio.ByteBuffer;
import java.util.EnumSet;

/*
+-----------------------------------------------+
| Length (24 bits) | Type (8 bits) | Flags (8 bits) | R (1 bit) | Stream ID (31 bits) |
+-----------------------------------------------+
|                   Frame Payload                 |
+-----------------------------------------------+
 */
public class FrameHeader {
    public int FrameLength;
    public FrameType FrameType;
    public EnumSet<FrameFlag> FrameFlags;
    public int StreamID;

    public FrameHeader(int frameLength, FrameType frameType, EnumSet<FrameFlag> frameFlag, int streamID) {
        FrameLength = frameLength;
        FrameType = frameType;
        FrameFlags = frameFlag;
        StreamID = streamID;
    }

    // buf.size() >= 9
    public static FrameHeader parse(ByteBuffer buf) {
        int b1 = buf.get() & 0xff;
        int b2 = buf.get() & 0xff;
        int b3 = buf.get() & 0xff;
        int length = (b1 << 16) | (b2 << 8) | b3;

        // get 8 bits frame type
        int b4 = buf.get() & 0xff;
        FrameType type = org.example.http2.FrameType.fromTypeCode(b4);

        int flagsByte = buf.get() & 0xff;
        EnumSet<FrameFlag> flags = FrameFlag.parse(flagsByte);

        int streamId = buf.getInt() & 0x7fffffff;

        return new FrameHeader(length, type, flags, streamId);
    }

    @Override
    public String toString() {
        return "FrameHeader{" +
                "FrameLength=" + FrameLength +
                ", FrameType=" + FrameType +
                ", FrameFlags=" + FrameFlags +
                ", StreamID=" + StreamID +
                '}';
    }
}
