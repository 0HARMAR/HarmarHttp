package org.example.http2;

import java.nio.ByteBuffer;

public class Frame {
    protected FrameHeader header;
    protected byte[] payload;   // 原始负载数据

    public Frame(FrameHeader header, byte[] payload) {
        this.header = header;
        this.payload = payload;
    }

    public FrameHeader getHeader() {
        return header;
    }

    public byte[] getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "Frame{" +
                "header=" + header +
                ", payloadLength=" + (payload != null ? payload.length : 0) +
                '}';
    }

    public byte[] toBytes() {
        int payloadLength = payload != null ? payload.length : 0;

        // 确保 header.FrameLength 与实际 payload 长度一致
        header.FrameLength = payloadLength;

        ByteBuffer buf = ByteBuffer.allocate(9 + payloadLength);

        // 写入 Length（24 bit）
        buf.put((byte) ((payloadLength >> 16) & 0xFF));
        buf.put((byte) ((payloadLength >> 8) & 0xFF));
        buf.put((byte) (payloadLength & 0xFF));

        // 写入 Type（8 bit）
        buf.put((byte) header.FrameType.getTypeCode());

        // 写入 Flags（8 bit）
        int flagsByte = 0;
        for (FrameFlag flag : header.FrameFlags) {
            flagsByte |= flag.getFlagBit();
        }
        buf.put((byte) flagsByte);

        // 写入 StreamID（31 bit，高位保留为 0）
        buf.putInt(header.StreamID & 0x7FFFFFFF);

        // 写入 Payload
        if (payload != null) {
            buf.put(payload);
        }

        return buf.array();
    }

}
