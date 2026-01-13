package org.example;

import java.nio.ByteBuffer;

public class ProtocolDetector {

    private static final byte[] HTTP2_PREFACE = new byte[] {
            'P','R','I',' ','*',' ','H','T','T','P','/','2','.','0','\r','\n',
            '\r','\n',
            'S','M','\r','\n',
            '\r','\n'
    };

    public static Protocol detect(ByteBuffer buf) {
        if (buf.remaining() < HTTP2_PREFACE.length) {
            return null; // 数据不够，继续读
        }

        buf.mark();

        for (byte b : HTTP2_PREFACE) {
            if (buf.get() != b) {
                buf.reset();
                return Protocol.HTTP1;
            }
        }

        return Protocol.HTTP2;
    }
}
